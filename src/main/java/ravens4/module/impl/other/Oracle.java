package ravens4.module.impl.other;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import ravens4.Raven;
import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oracle: an AI helper in your chat. Turn it on and type {@code .ask <question>} (aliases .ai, .oracle).
 * The answer is printed in chat. It calls the Groq API (OpenAI compatible), so it needs a Groq API key.
 * The key is kept in {@code config/oracle.properties} in the game folder, never in profiles, and it is set
 * with the client-side command {@code /oracle key <key>} (a "/" client command is never sent to the server).
 */
public class Oracle extends Module {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODELS_URL = "https://api.groq.com/openai/v1/models";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    private static final String PROMPT_RESOURCE = "/assets/ravens4/system_prompts/system_prompt.txt";
    private static final int MAX_PROMPT_LENGTH = 8000;
    private static final String DEFAULT_PROMPT = "You are Oracle, a helper inside the chat of a Minecraft Java 1.8.9 client. "
            + "Answer in plain text only: no markdown, no bullet lists, no code blocks. "
            + "Keep answers short, at most three sentences, unless the player asks for more detail. "
            + "Never ask for or repeat passwords or API keys. "
            + "Adapt your tone, detail and length to what you know about the player.";
    private static final String LEARN_PROMPT = " You can remember things between sessions. When the player tells you a lasting fact "
            + "(their name, goals, base location, playstyle, preferences) or asks you to remember something, add a line at the very end "
            + "of your reply in exactly this form: [[remember: short fact]]. If they ask you to forget something, add "
            + "[[forget: a few words from that fact]]. Never store passwords, keys or private contact details.";
    private static final Pattern REMEMBER = Pattern.compile("\\[\\[\\s*remember\\s*:\\s*(.+?)\\s*\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET = Pattern.compile("\\[\\[\\s*forget\\s*:\\s*(.+?)\\s*\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_LIKE = Pattern.compile("sk-[A-Za-z0-9_\\-]{10,}|[A-Za-z0-9_\\-]{40,}");
    private static final int MAX_NOTE_LENGTH = 140;
    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int LINE_WIDTH = 56;

    private final SliderSetting maxTokens;
    private final SliderSetting memory;
    private final ButtonSetting showQuestion;
    private final ButtonSetting learn;
    private final ButtonSetting useContext;
    private final SliderSetting maxNotes;

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final List<String[]> history = new ArrayList<>(); // {role, text}
    private final List<String> notes = new ArrayList<>(); // long term memory, saved to disk
    private volatile String apiKey = "";
    private volatile String model = DEFAULT_MODEL;
    private boolean warnedNoKey;
    private volatile String basePrompt = DEFAULT_PROMPT;

    public Oracle() {
        super("Oracle", category.other);
        this.registerSetting(maxTokens = new SliderSetting("Max reply tokens", 300, 100, 800, 50));
        this.registerSetting(memory = new SliderSetting("Memory", " exchanges", 3, 0, 6, 1));
        this.registerSetting(showQuestion = new ButtonSetting("Show question", true));
        this.registerSetting(learn = new ButtonSetting("Learn from chats", true));
        this.registerSetting(useContext = new ButtonSetting("Use game context", true));
        this.registerSetting(maxNotes = new SliderSetting("Max notes", 30, 10, 60, 5));
        loadConfig();
        loadNotes();
        loadPrompt();
    }

    @Override
    public void onEnable() {
        loadConfig();
        loadNotes();
        loadPrompt();
        warnedNoKey = false;
        if (apiKey.isEmpty()) {
            Utils.sendMessage("&dOracle&7: set your Groq key with &f/oracle key <key>&7, then ask with &f.ask <question>");
            warnedNoKey = true;
        } else {
            Utils.sendMessage("&dOracle&7: ready. Ask with &f.ask <question>");
        }
    }

    @Override
    public String getInfo() {
        return noteCount() + " notes";
    }

    // ------------------------------------------------------------------ config

    private File configFile() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "oracle.properties");
    }

    private void loadConfig() {
        File f = configFile();
        if (!f.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            apiKey = p.getProperty("groq_api_key", "").trim();
            String m = p.getProperty("groq_model", DEFAULT_MODEL).trim();
            model = m.isEmpty() ? DEFAULT_MODEL : m;
        } catch (Exception ignored) {
        }
    }

    // true if the file was written
    private boolean saveConfig() {
        Properties p = new Properties();
        p.setProperty("groq_api_key", apiKey);
        p.setProperty("groq_model", model);
        try (FileOutputStream out = new FileOutputStream(configFile())) {
            p.store(out, "Oracle settings. Keep this file private: it holds your API key.");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setApiKey(String key) {
        apiKey = key == null ? "" : key.trim();
        return saveConfig();
    }

    public boolean setModel(String id) {
        model = id == null || id.trim().isEmpty() ? DEFAULT_MODEL : id.trim();
        return saveConfig();
    }

    public String getConfigPath() {
        return configFile().getAbsolutePath();
    }

    // first and last characters only, never the whole key
    public String maskedKey() {
        String k = apiKey;
        if (k.isEmpty()) {
            return "";
        }
        return k.length() <= 8 ? "****" : k.substring(0, 4) + "..." + k.substring(k.length() - 4);
    }

    // asks Groq (in the background) whether the key works and reports the result as a chat line
    public void verifyKey(java.util.function.Consumer<String> report) {
        final String key = apiKey;
        Raven.getCachedExecutor().execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MODELS_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("User-Agent", "Oracle/1.0 (Minecraft client)");
                int code = c.getResponseCode();
                c.disconnect();
                if (code == 200) {
                    report.accept("&aKey works. You can ask now with &f.ask <question>");
                } else if (code == 401 || code == 403) {
                    report.accept("&cGroq rejected this key. Check it and run &f/oracle key <key>&c again.");
                } else {
                    report.accept("&eSaved, but Groq answered with code " + code + " when checking it.");
                }
            } catch (Throwable t) {
                report.accept("&eSaved, but couldn't reach Groq to check it (is the internet on?).");
            }
        });
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }

    public String getModel() {
        return model;
    }

    public void clearMemory() {
        synchronized (history) {
            history.clear();
        }
    }

    // prints the model ids your key can use, so /oracle model <name> never has to be guessed
    public void listModels() {
        if (apiKey.isEmpty()) {
            Utils.sendMessage("&dOracle&7: set your key first with &f/oracle key <key>");
            return;
        }
        Raven.getCachedExecutor().execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MODELS_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("Authorization", "Bearer " + apiKey);
                c.setRequestProperty("User-Agent", "Oracle/1.0 (Minecraft client)");
                int code = c.getResponseCode();
                InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String text = stream == null ? "" : read(stream);
                c.disconnect();
                if (code >= 400) {
                    Utils.sendMessage("&dOracle&7: &c" + describeError(code, text));
                    return;
                }
                JsonArray data = new JsonParser().parse(text).getAsJsonObject().getAsJsonArray("data");
                Utils.sendMessage("&dOracle&7: models you can use (&f/oracle model <name>&7):");
                int shown = 0;
                if (data != null) {
                    for (JsonElement e : data) {
                        JsonObject m = e.getAsJsonObject();
                        if (m.has("id") && shown < 25) {
                            Utils.sendMessage("&7- &f" + m.get("id").getAsString());
                            shown++;
                        }
                    }
                }
            } catch (Throwable t) {
                Utils.sendMessage("&dOracle&7: &ccouldn't load the model list (" + t.getClass().getSimpleName() + ").");
            }
        });
    }

    // ------------------------------------------------------------------ system prompt

    // Order: config/oracle_system_prompt.txt in the game folder (edit it on the device, no rebuild needed),
    // then assets/ravens4/system_prompts/system_prompt.txt inside the mod, then the built-in default.
    private void loadPrompt() {
        String text = null;
        File external = new File(configFile().getParentFile(), "oracle_system_prompt.txt");
        if (external.isFile()) {
            try (InputStream in = new FileInputStream(external)) {
                text = read(in);
            } catch (Exception ignored) {
            }
        }
        if (text == null || text.trim().isEmpty()) {
            try (InputStream in = Oracle.class.getResourceAsStream(PROMPT_RESOURCE)) {
                if (in != null) {
                    text = read(in);
                }
            } catch (Exception ignored) {
            }
        }
        text = text == null ? "" : text.trim();
        if (text.length() > MAX_PROMPT_LENGTH) {
            text = text.substring(0, MAX_PROMPT_LENGTH);
        }
        basePrompt = text.isEmpty() ? DEFAULT_PROMPT : text;
    }

    // ------------------------------------------------------------------ long term memory

    private File notesFile() {
        return new File(configFile().getParentFile(), "oracle-memory.txt");
    }

    private void loadNotes() {
        File f = notesFile();
        if (!f.exists()) {
            return;
        }
        List<String> loaded = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    loaded.add(line);
                }
            }
        } catch (Exception ignored) {
            return;
        }
        synchronized (notes) {
            notes.clear();
            notes.addAll(loaded);
        }
    }

    private void saveNotes() {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(notesFile()), StandardCharsets.UTF_8))) {
            synchronized (notes) {
                for (String n : notes) {
                    w.println(n);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public int noteCount() {
        synchronized (notes) {
            return notes.size();
        }
    }

    public List<String> getNotes() {
        synchronized (notes) {
            return new ArrayList<>(notes);
        }
    }

    // returns true if the note was stored
    private boolean addNote(String note) {
        String n = note == null ? "" : note.replaceAll("\\s+", " ").trim();
        if (n.isEmpty() || KEY_LIKE.matcher(n).find()) {
            return false;
        }
        if (n.length() > MAX_NOTE_LENGTH) {
            n = n.substring(0, MAX_NOTE_LENGTH);
        }
        synchronized (notes) {
            for (String existing : notes) {
                if (existing.equalsIgnoreCase(n)) {
                    return false;
                }
            }
            notes.add(n);
            while (notes.size() > (int) maxNotes.getInput()) {
                notes.remove(0);
            }
        }
        saveNotes();
        return true;
    }

    private int removeNotesContaining(String words) {
        String w = words == null ? "" : words.toLowerCase().trim();
        if (w.isEmpty()) {
            return 0;
        }
        int removed = 0;
        synchronized (notes) {
            for (int i = notes.size() - 1; i >= 0; i--) {
                if (notes.get(i).toLowerCase().contains(w)) {
                    notes.remove(i);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            saveNotes();
        }
        return removed;
    }

    // 1-based index, as shown by ".ask memory"
    public boolean forgetNote(int number) {
        boolean ok;
        synchronized (notes) {
            ok = number >= 1 && number <= notes.size();
            if (ok) {
                notes.remove(number - 1);
            }
        }
        if (ok) {
            saveNotes();
        }
        return ok;
    }

    public void forgetAllNotes() {
        synchronized (notes) {
            notes.clear();
        }
        saveNotes();
    }

    // takes the [[remember: ...]] / [[forget: ...]] lines out of the reply and applies them
    private String applyMemoryMarkers(String raw, List<String> hints) {
        String text = raw;
        Matcher f = FORGET.matcher(text);
        while (f.find()) {
            if (removeNotesContaining(f.group(1)) > 0) {
                hints.add("forgot: " + f.group(1));
            }
        }
        text = FORGET.matcher(text).replaceAll("");
        if (learn.isToggled()) {
            Matcher r = REMEMBER.matcher(text);
            while (r.find()) {
                if (addNote(r.group(1))) {
                    hints.add("remembered: " + r.group(1));
                }
            }
        }
        return REMEMBER.matcher(text).replaceAll("").trim();
    }

    private String systemPrompt(String context) {
        StringBuilder sb = new StringBuilder(basePrompt);
        if (learn.isToggled()) {
            sb.append(LEARN_PROMPT);
        }
        List<String> known = getNotes();
        if (!known.isEmpty()) {
            sb.append("\n\nWhat you know about this player:");
            for (String n : known) {
                sb.append("\n- ").append(n);
            }
        }
        if (context != null) {
            sb.append("\n\nCurrent game state: ").append(context);
        }
        return sb.toString();
    }

    // what is going on in the game right now, so answers fit the situation. Built on the game thread.
    private String buildContext() {
        if (!useContext.isToggled() || !Utils.nullCheck()) {
            return null;
        }
        try {
            BlockPos p = new BlockPos(mc.thePlayer);
            ItemStack held = mc.thePlayer.getHeldItem();
            String dim = mc.theWorld.provider.getDimensionId() == -1 ? "nether"
                    : mc.theWorld.provider.getDimensionId() == 1 ? "end" : "overworld";
            return (mc.isSingleplayer() ? "singleplayer" : "multiplayer") + ", " + dim
                    + ", position " + p.getX() + " " + p.getY() + " " + p.getZ()
                    + ", biome " + mc.theWorld.getBiomeGenForCoords(p).biomeName
                    + ", health " + (int) mc.thePlayer.getHealth()
                    + ", food " + mc.thePlayer.getFoodStats().getFoodLevel()
                    + ", holding " + (held == null ? "nothing" : held.getDisplayName())
                    + ", day time " + (mc.theWorld.getWorldTime() % 24000L)
                    + (mc.theWorld.isRaining() ? ", raining" : "");
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ asking

    public void ask(String question) {
        if (question == null || question.trim().isEmpty()) {
            return;
        }
        if (apiKey.isEmpty()) {
            loadConfig();
        }
        if (apiKey.isEmpty()) {
            Utils.sendMessage("&dOracle&7: no Groq API key yet. Use &f/oracle key <gsk_...>&7 (a / command stays on your side).");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            Utils.sendMessage("&dOracle&7: still thinking about the last question...");
            return;
        }
        final String q = question.trim().length() > MAX_QUESTION_LENGTH
                ? question.trim().substring(0, MAX_QUESTION_LENGTH) : question.trim();
        if (showQuestion.isToggled()) {
            Utils.sendMessage("&dYou&7: &f" + q);
        }
        final String context = buildContext();
        Raven.getCachedExecutor().execute(() -> {
            try {
                String raw = request(q, context);
                if (raw != null) {
                    List<String> hints = new ArrayList<>();
                    String answer = applyMemoryMarkers(raw, hints);
                    remember(q, answer);
                    if (answer.isEmpty()) {
                        Utils.sendMessage("&dOracle&7: noted.");
                    } else {
                        printAnswer(answer);
                    }
                    for (String hint : hints) {
                        Utils.sendMessage("&8Oracle " + hint);
                    }
                }
            } catch (Throwable t) {
                Utils.sendMessage("&dOracle&7: &ccouldn't get an answer (" + t.getClass().getSimpleName() + ").");
            } finally {
                busy.set(false);
            }
        });
    }

    private void remember(String question, String answer) {
        int keep = (int) memory.getInput() * 2;
        synchronized (history) {
            if (keep <= 0) {
                history.clear();
                return;
            }
            history.add(new String[]{"user", question});
            history.add(new String[]{"assistant", answer});
            while (history.size() > keep) {
                history.remove(0);
            }
        }
    }

    // returns the answer text, or null after printing an error message
    private String request(String question, String context) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", (int) maxTokens.getInput());
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(context)));
        synchronized (history) {
            for (String[] m : history) {
                messages.add(message(m[0], m[1]));
            }
        }
        messages.add(message("user", question));
        body.add("messages", messages);

        HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(40000);
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("User-Agent", "Oracle/1.0 (Minecraft client)");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = stream == null ? "" : read(stream);
        c.disconnect();

        if (code >= 400) {
            Utils.sendMessage("&dOracle&7: &c" + describeError(code, text));
            return null;
        }
        JsonObject root = new JsonParser().parse(text).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices != null && choices.size() > 0) {
            JsonObject first = choices.get(0).getAsJsonObject();
            if (first.has("message") && first.get("message").isJsonObject()) {
                JsonObject msg = first.getAsJsonObject("message");
                if (msg.has("content") && !msg.get("content").isJsonNull()) {
                    sb.append(msg.get("content").getAsString());
                }
            }
        }
        String answer = sb.toString().trim();
        if (answer.isEmpty()) {
            Utils.sendMessage("&dOracle&7: &cempty answer.");
            return null;
        }
        return answer;
    }

    private static JsonObject message(String role, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", text);
        return o;
    }

    private static String describeError(int code, String text) {
        String detail = "";
        try {
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject() && root.getAsJsonObject("error").has("message")) {
                detail = root.getAsJsonObject("error").get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        if (code == 401) {
            return "the API key was rejected. Set it again with /oracle key <key>.";
        }
        if (code == 404) {
            return "model not found. See the list with /oracle models.";
        }
        if (code == 400 && detail.toLowerCase().contains("model")) {
            return "that model isn't available: " + detail + " (see /oracle models)";
        }
        if (code == 429) {
            return "rate limited, wait a moment and try again.";
        }
        return "error " + code + (detail.isEmpty() ? "" : ": " + detail);
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ output

    private static void printAnswer(String answer) {
        String clean = answer
                .replace("**", "").replace("__", "").replace("`", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "")
                .replaceAll("\\s*\\n+\\s*", " ")
                .trim();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : clean.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > LINE_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        for (int i = 0; i < lines.size(); i++) {
            Utils.sendMessage((i == 0 ? "&dOracle&7: &f" : "&f") + lines.get(i));
        }
    }
}
