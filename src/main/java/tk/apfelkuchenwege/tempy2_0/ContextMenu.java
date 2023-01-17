package tk.apfelkuchenwege.tempy2_0;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;


public class ContextMenu extends ListenerAdapter {
    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        System.out.println("User Context Menu");
        if (event.getName().equals("e")) {
            Main.mobbingMembers.add(event.getTargetMember());
            new Thread(() -> {
                AudioChannel vc = event.getTargetMember().getVoiceState().getChannel();
                for (int i = 0; i < 3; i++) {
                    event.getGuild().moveVoiceMember(event.getTargetMember(), event.getGuild().getAfkChannel()).complete();
                    event.getGuild().moveVoiceMember(event.getTargetMember(), vc).complete();
                }
                Main.mobbingMembers.remove(event.getTargetMember());
            }).start();
        }
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        System.out.println("Message Context Menu");
        if (event.getName().equals("Count words")) {
            event.reply("Words: " + event.getTarget().getContentRaw().split("\\s+").length).queue();
        }
    }
}
