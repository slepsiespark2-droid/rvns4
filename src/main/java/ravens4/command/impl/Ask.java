package ravens4.command.impl;

import ravens4.command.Command;
import ravens4.command.CommandInput;
import ravens4.module.ModuleManager;

public class Ask extends Command {
    public Ask() {
        super("ask", "ask", "ai", "oracle");
    }

    @Override
    public void execute(CommandInput input) {
        if (ModuleManager.oracle == null || !ModuleManager.oracle.isEnabled()) {
            replyWithHeader("&7Turn the &dOracle&7 module on first.");
            return;
        }
        if (input.argumentCount() == 0) {
            replyWithHeader("&7Usage: &f" + prefixed("ask") + " <question>&7  (&f" + prefixed("ask") + " clear&7 forgets the conversation)");
            return;
        }
        if (input.argumentCount() == 1 && "clear".equalsIgnoreCase(input.getArgument(0))) {
            ModuleManager.oracle.clearMemory();
            replyWithHeader("&7Oracle forgot the conversation.");
            return;
        }
        String first = input.getArgument(0);
        if (input.argumentCount() == 1 && ("memory".equalsIgnoreCase(first) || "notes".equalsIgnoreCase(first))) {
            java.util.List<String> notes = ModuleManager.oracle.getNotes();
            if (notes.isEmpty()) {
                replyWithHeader("&7Oracle hasn't learned anything about you yet.");
                return;
            }
            replyWithHeader("&7Oracle remembers (&f" + prefixed("ask") + " forget <number|all>&7 to remove):");
            for (int i = 0; i < notes.size(); i++) {
                reply("&7" + (i + 1) + ". &f" + notes.get(i));
            }
            return;
        }
        if (input.argumentCount() == 2 && "forget".equalsIgnoreCase(first)) {
            String what = input.getArgument(1);
            if ("all".equalsIgnoreCase(what)) {
                ModuleManager.oracle.forgetAllNotes();
                replyWithHeader("&7Oracle forgot everything it learned about you.");
                return;
            }
            try {
                if (ModuleManager.oracle.forgetNote(Integer.parseInt(what))) {
                    replyWithHeader("&7Forgotten.");
                } else {
                    replyWithHeader("&7No note with that number. See &f" + prefixed("ask") + " memory&7.");
                }
            } catch (NumberFormatException e) {
                replyWithHeader("&7Use a number or &fall&7.");
            }
            return;
        }
        ModuleManager.oracle.ask(input.joinArguments(0));
    }
}
