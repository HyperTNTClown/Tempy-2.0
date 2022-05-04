package tk.apfelkuchenwege.tempy2_0;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Main {
    ArrayList<AudioChannel> channels = new ArrayList<>();
    ArrayList<AudioChannel> toRemove = new ArrayList<>();
    HashMap<AudioChannel, TextChannel> matchingTextChannels = new HashMap<>();
    HashMap<Member, AudioChannel> memberAudioChannels = new HashMap<>();
    static HashMap<Guild, AudioChannel> guildAudioCreationChannels = new HashMap<>();
    static Map<String, String> env = System.getenv();

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        JDABuilder builder = JDABuilder.createDefault(env.get("TOKEN"));
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setCompression(Compression.NONE);
        builder.setActivity(Activity.listening("niemandem"));
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.enableCache(CacheFlag.EMOTE);
        builder.setEnabledIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS);
        builder.setEventManager(new AnnotatedEventManager());
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.addEventListeners(new Main());

        JDA jda = builder.build();

        jda.upsertCommand("test", "fisch");
        jda.awaitReady();
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("config.properties"));

            for (String key : prop.stringPropertyNames()) {
                guildAudioCreationChannels.put(jda.getGuildById(key), jda.getGuildById(key).getVoiceChannelById(prop.getProperty(key)));
            }
        } catch (FileNotFoundException e) {
            File file = new File("config.properties");
            file.createNewFile();
        }

    }
    @SubscribeEvent
    public void onVoiceChannelEvent(GenericGuildVoiceEvent e) {
        if (e instanceof GuildVoiceMoveEvent || e instanceof GuildVoiceLeaveEvent) {
            if (e instanceof GuildVoiceLeaveEvent) {
                if (channels.contains(((GuildVoiceLeaveEvent) e).getChannelLeft())){
                    matchingTextChannels.get(((GuildVoiceLeaveEvent) e).getChannelLeft()).sendMessage("<@" + e.getMember().getId() + "> hat den Kanal verlassen").queue();
                    try {
                        matchingTextChannels.get(((GuildVoiceLeaveEvent) e).getChannelLeft()).upsertPermissionOverride(((GuildVoiceLeaveEvent) e).getMember()).setDenied(Permission.VIEW_CHANNEL).queue();
                    } catch (Exception ex) {
                        return;
                    }
                    if (((GuildVoiceLeaveEvent) e).getChannelLeft().getMembers().isEmpty()) {
                        matchingTextChannels.get(((GuildVoiceLeaveEvent) e).getChannelLeft()).delete().queue();
                        matchingTextChannels.remove(((GuildVoiceLeaveEvent) e).getChannelLeft());
                        ((GuildVoiceLeaveEvent) e).getChannelLeft().delete().queue();
                        channels.remove(((GuildVoiceLeaveEvent) e).getChannelLeft());
                    }
                }
            } else {
                if (channels.contains(((GuildVoiceMoveEvent) e).getChannelLeft())){
                    matchingTextChannels.get(((GuildVoiceMoveEvent) e).getChannelLeft()).sendMessage("<@" + e.getMember().getId() + "> hat den Kanal verlassen").queue();
                    try {
                    matchingTextChannels.get(((GuildVoiceMoveEvent) e).getChannelLeft()).upsertPermissionOverride(((GuildVoiceMoveEvent) e).getMember()).setAllowed(Permission.VIEW_CHANNEL).queue();
                    } catch (Exception ex) {
                        return;
                    }
                    if (((GuildVoiceMoveEvent) e).getChannelLeft().getMembers().isEmpty()) {
                        matchingTextChannels.get(((GuildVoiceMoveEvent) e).getChannelLeft()).delete().queue();
                        matchingTextChannels.remove(((GuildVoiceMoveEvent) e).getChannelLeft());
                        ((GuildVoiceMoveEvent) e).getChannelLeft().delete().queue();
                        channels.remove(((GuildVoiceMoveEvent) e).getChannelLeft());
                    }
                }
            }
        }
        Category category = null;
        for ( Category c : e.getGuild().getCategories()) {
            if (c.getName().contains("Eigene Ka")) {
                category = c;
                break;
            }
        }
        if (e.getVoiceState().getChannel() == null) {
            return;
        }
        if (channels.contains(e.getVoiceState().getChannel())) {
            System.out.println("Joined existing channel");
            matchingTextChannels.get(e.getVoiceState().getChannel()).upsertPermissionOverride(e.getMember()).setAllowed(Permission.VIEW_CHANNEL).queue();
            matchingTextChannels.get(e.getVoiceState().getChannel()).sendMessage("<@" + e.getMember().getId() + "> ist dem Kanal beigetreten").queue();
        }
        try {
            System.out.println(guildAudioCreationChannels.get(e.getGuild().getId()));
            if (e.getVoiceState().getChannel().getId().equals(guildAudioCreationChannels.get(e.getGuild()).getId())) {
                if (category == null) {
                    category = e.getGuild().createCategory("Eigene Kan\u00e4le").complete();
                }
                System.out.println("Joined Creation channel");
                AudioChannel vc = e.getGuild()
                        .createVoiceChannel(e.getMember().getUser().getName() + "s Sprachkanal", category)
                        .addMemberPermissionOverride(Long.parseLong(e.getMember().getId()), Permission.ALL_PERMISSIONS, 0L)
                        .complete();

                e.getGuild().moveVoiceMember(e.getMember(), vc).queue();
                channels.add(vc);

                TextChannel text = e.getGuild().createTextChannel(e.getMember().getUser().getName() + "s Textkanal", category)
                        .addRolePermissionOverride(Long.parseLong(e.getGuild().getPublicRole().getId()), 0L, Permission.VIEW_CHANNEL.getRawValue())
                        .addMemberPermissionOverride(Long.parseLong(e.getMember().getId()), Permission.ALL_PERMISSIONS, 0L)
                        .complete();

                matchingTextChannels.put(vc, text);
                memberAudioChannels.put(e.getMember(), vc);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) throws IOException {
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
            Properties prop = new Properties();
            for (Map.Entry<Guild, AudioChannel> entry : guildAudioCreationChannels.entrySet()) {
                prop.put(entry.getKey().getId(), entry.getValue().getId());
            }
            prop.store(new FileOutputStream("config.properties"), null);
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