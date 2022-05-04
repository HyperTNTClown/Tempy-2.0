import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {
    ArrayList<AudioChannel> channels = new ArrayList<>();
    ArrayList<AudioChannel> toRemove = new ArrayList<>();
    HashMap<AudioChannel, TextChannel> matchingTextChannels = new HashMap<>();
    HashMap<Member, AudioChannel> memberAudioChannels = new HashMap<>();
    HashMap<Guild, AudioChannel> guildAudioCreationChannels = new HashMap<>();

    public static void main(String[] args) throws LoginException, InterruptedException {
        JDABuilder builder = JDABuilder.createDefault("OTcxNDIwODY2MDQwNzA1MDU0.YnKQLg.az9gNQpY4k7FyBzoT9TDAKnElgY");
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setCompression(Compression.NONE);
        builder.setActivity(Activity.listening("my life"));
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.enableCache(CacheFlag.EMOTE);
        builder.setEnabledIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS);
        builder.setEventManager(new AnnotatedEventManager());
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.addEventListeners(new Main());

        JDA jda = builder.build();

        jda.upsertCommand("test", "fisch");
        jda.awaitReady();
    }

    @SubscribeEvent
    public void onVoiceChannelEvent(GenericGuildVoiceEvent e) {
        Category category = null;
        for ( Category c : e.getGuild().getCategories()) {
            if (c.getName().equals("Eigene Kanäle")) {
                category = c;
                break;
            }
        }
        //System.out.println(e.getVoiceState().getChannel().getId());
        if (e.getVoiceState().getChannel() == null) {
            return;
        }
        if (channels.contains(e.getVoiceState().getChannel())) {
            System.out.println("Joined existing channel");
            matchingTextChannels.get(e.getVoiceState().getChannel()).upsertPermissionOverride(e.getMember()).setAllowed(Permission.ALL_TEXT_PERMISSIONS).queue();
            matchingTextChannels.get(e.getVoiceState().getChannel()).sendMessage(e.getMember().getUser().getName() + " ist dem Kanal beigetreten").queue();
        } else if (memberAudioChannels.getOrDefault(e.getMember(), null) != null) {
            System.out.println("Probably left a channel, removing from the list");
            AudioChannel leftVC = memberAudioChannels.get(e.getMember());
            TextChannel leftText = matchingTextChannels.get(leftVC);
            leftText.getPermissionContainer().upsertPermissionOverride(e.getMember()).setDenied(Permission.ALL_TEXT_PERMISSIONS).queue();
            leftText.sendMessage(e.getMember().getUser().getName() + " hat den Kanal verlassen").queue();
            memberAudioChannels.remove(e.getMember());
            for (AudioChannel channel : channels) {
                if (channel.getMembers().isEmpty()) {
                    channel.delete().queue();
                    matchingTextChannels.get(channel).delete().queue();
                    toRemove.add(channel);
                    matchingTextChannels.remove(channel);
                }
            }
            for (AudioChannel channel : toRemove) {
                channels.remove(channel);
            }
        } else if (e.getVoiceState().getChannel().getId().equals(guildAudioCreationChannels.get(e.getGuild()).getId())) {
            if (category == null) {
                category = e.getGuild().createCategory("Eigene Kanäle").complete();
            }
            System.out.println("Joined Creation channel");
            AudioChannel vc = e.getGuild()
                    .createVoiceChannel(e.getMember().getUser().getName() + "s Sprachkanal", category)
                    .addMemberPermissionOverride(Long.parseLong(e.getMember().getId()), Permission.ALL_VOICE_PERMISSIONS, 0L)
                    .complete();

            e.getGuild().moveVoiceMember(e.getMember(), vc).queue();
            channels.add(vc);

            TextChannel text = e.getGuild().createTextChannel(e.getMember().getUser().getName() + "s Textkanal", category)
                    .addMemberPermissionOverride(Long.parseLong(e.getMember().getId()), Permission.ALL_TEXT_PERMISSIONS, 0L)
                    .addPermissionOverride(e.getGuild().getPublicRole(), 0L, Permission.ALL_TEXT_PERMISSIONS)
                    .complete();

            matchingTextChannels.put(vc, text);
            memberAudioChannels.put(e.getMember(), vc);
        }
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (event.getMessage().getContentRaw().equalsIgnoreCase("!test")) {
            event.getMessage().delete().queue();
            event.getChannel().sendMessage("Test").queue();
        }
        if (event.getMessage().getContentRaw().contains("!setchannel ")) {
            String[] args = event.getMessage().getContentRaw().split(" ");
            if (args.length != 2) {
                event.getChannel().sendMessage("Wat willst du denn von mir, denn das war irgendwie falsch\nProbiers nochmal! (!setchannel [channel-id])").queue();
                return;
            }
            long inLong;
            try {
                inLong = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("Das war keine Zahl!\nProbiers nochmal! (!setchannel [channel-id])").queue();
                return;
            }
            if (event.getGuild().getVoiceChannelById(inLong) == null) {
                event.getChannel().sendMessage("Dieser Kanal existiert garnicht!\nWas ist eigentlich falsch bei dir").queue();
            }
            guildAudioCreationChannels.put(event.getGuild(), event.getGuild().getVoiceChannelById(inLong));
            event.getChannel().sendMessage("Erstellungskanal auf die ID " + args[1] + " gesetzt").queue();
        }
    }

    @SubscribeEvent
    public void deleteOnShutdown(ReadyEvent event) {
        Thread shutdownThread = new Thread(() -> {
            for (AudioChannel channel : channels) {
                channel.delete().queue();
            }
            for (TextChannel channel : matchingTextChannels.values()) {
                channel.delete().queue();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }

}