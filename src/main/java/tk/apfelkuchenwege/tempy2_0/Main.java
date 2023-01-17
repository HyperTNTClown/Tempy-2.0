package tk.apfelkuchenwege.tempy2_0;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class Main {
    ArrayList<AudioChannel> channels = new ArrayList<>();
    ArrayList<AudioChannel> toRemove = new ArrayList<>();
    HashMap<AudioChannel, TextChannel> matchingTextChannels = new HashMap<>();
    HashMap<Member, AudioChannel> memberAudioChannels = new HashMap<>();
    HashMap<Member, AudioChannel> savedAfkDeafMembers = new HashMap<>();

    ArrayList<Member> mobbingMembers = new ArrayList<>();

    static HashMap<Guild, AudioChannel> guildAudioCreationChannels = new HashMap<>();
    static Map<String, String> env = System.getenv();
    static JDA jda;

    ArrayList<Member> lockedMembers = new ArrayList<>();
    Thread lockThread = new Thread(() -> {
        while (true) {
            if (lockedMembers.size() == 0) {
                return;
            }
            final ArrayList<Member> members = (ArrayList<Member>) lockedMembers.clone();
            for (Member member : members) {
                if (member.getVoiceState().getChannel() != null) {
                    member.getGuild().moveVoiceMember(member, member.getGuild().getAfkChannel()).complete();
                }
            }
        }
    });
    Thread tLockThread = new Thread(() -> {
        while (true) {
            if (lockedMembers.size() == 0) {
                return;
            }
            final ArrayList<Member> members = (ArrayList<Member>) lockedMembers.clone();
            for (Member member : members) {
                if (member.getVoiceState().getChannel() != null) {
                    member.getGuild().moveVoiceMember(member, member.getGuild().getAfkChannel()).complete();
                }
            }
        }
    });

    static TimerTask task = new TimerTask() {
        @Override
        public void run() {
            randomMove();
        }
    };

    public static void main(String[] args) throws InterruptedException, IOException {
        JDABuilder builder = JDABuilder.createDefault(env.get("TOKEN"));
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setCompression(Compression.NONE);
        builder.setActivity(Activity.listening("dir"));
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.enableCache(CacheFlag.EMOJI);
        builder.setEnabledIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS_AND_STICKERS, GatewayIntent.SCHEDULED_EVENTS, GatewayIntent.MESSAGE_CONTENT);
        builder.setEventManager(new AnnotatedEventManager());
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.addEventListeners(new Main());

        jda = builder.build();

        jda.awaitReady();


        System.out.println(jda.getSelfUser());
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("config.properties"));

            for (String key : prop.stringPropertyNames()) {
                guildAudioCreationChannels.put(jda.getGuildById(key), jda.getGuildById(key).getVoiceChannelById(prop.getProperty(key)));
            }
        } catch (Exception e) {
            File file = new File("config.properties");
            file.createNewFile();
        }

        jda.getGuildById("886658879797215303").updateCommands().addCommands(
                Commands.context(Command.Type.USER, "e"),
                Commands.user("lock").setName("lock")
        ).queue();

        Timer timer = new Timer("Timer");
        timer.scheduleAtFixedRate(task, 0, 3600000/8);

    }

    @SubscribeEvent
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        System.out.println("User Context Menu: " + event.getName());
        if (event.getName().equals("e")) {
            if (event.getTargetMember().getVoiceState().getChannel() == null) {
                return;
            }
            mobbingMembers.add(event.getTargetMember());
            Thread t = new Thread(() -> {
                AudioChannel vc = event.getTargetMember().getVoiceState().getChannel();
                for (int i = 0; i < 10; i++) {
                    event.getGuild().moveVoiceMember(event.getTargetMember(), event.getGuild().getAfkChannel()).complete();
                    event.getGuild().moveVoiceMember(event.getTargetMember(), vc).complete();
                }
                mobbingMembers.remove(event.getTargetMember());
            });
            t.start();
        } else if (event.getName().equals("lock")) {
            if (event.getTargetMember().getVoiceState().getChannel() == null) {
                return;
            }
            if (lockedMembers.contains(event.getTargetMember())) {
                lockedMembers.remove(event.getTargetMember());
                mobbingMembers.remove(event.getTargetMember());
            } else {
                lockedMembers.add(event.getTargetMember());
                mobbingMembers.add(event.getTargetMember());
                if (!lockThread.isAlive()) {
                    lockThread = tLockThread;
                    lockThread.start();
                }
            }
        }
    }

    @SubscribeEvent
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        System.out.println("Message Context Menu");
        if (event.getName().equals("Count words")) {
            event.reply("Words: " + event.getTarget().getContentRaw().split("\\s+").length).queue();
        }
    }

    @SubscribeEvent
    public void onVoiceChannelEvent(GuildVoiceUpdateEvent e) {
        if (e != null) {
            if (e.getChannelJoined() == null) {
                if (channels.contains(e.getChannelLeft())){
                    matchingTextChannels.get(e.getChannelLeft()).sendMessage("<@" + e.getMember().getId() + "> hat den Kanal verlassen").queue();
                    try {
                        matchingTextChannels.get(e.getChannelLeft()).upsertPermissionOverride(e.getMember()).setDenied(Permission.VIEW_CHANNEL).queue();
                    } catch (Exception ex) {
                        return;
                    }
                    if (e.getChannelLeft().getMembers().isEmpty()) {
                        matchingTextChannels.get(e.getChannelLeft()).delete().queue();
                        matchingTextChannels.remove(e.getChannelLeft());
                        e.getChannelLeft().delete().queue();
                        channels.remove(e.getChannelLeft());
                    }
                }
            } else {
                if (channels.contains(e.getChannelLeft())){
                    matchingTextChannels.get(e.getChannelLeft()).sendMessage("<@" + e.getMember().getId() + "> hat den Kanal verlassen").queue();
                    try {
                    matchingTextChannels.get(e.getChannelLeft()).upsertPermissionOverride(e.getMember()).setAllowed(Permission.VIEW_CHANNEL).queue();
                    } catch (Exception ex) {
                        return;
                    }
                    if (e.getChannelLeft().getMembers().isEmpty()) {
                        matchingTextChannels.get(e.getChannelLeft()).delete().queue();
                        matchingTextChannels.remove(e.getChannelLeft());
                        e.getChannelLeft().delete().queue();
                        channels.remove(e.getChannelLeft());
                    }
                }
            }
        }
        Category category = null;
        for ( Category c : e.getGuild().getCategories()) {
            if (c.getName().contains("Channels")) {
                category = c;
                break;
            }
        } if (category == null) {
            category = e.getGuild().createCategory("Channels").complete();
        }
        if (e.getVoiceState().getChannel() == null) {
            return;
        }
        if (channels.contains(e.getVoiceState().getChannel())) {
            matchingTextChannels.get(e.getVoiceState().getChannel()).upsertPermissionOverride(e.getMember()).setAllowed(Permission.VIEW_CHANNEL).queue();
            matchingTextChannels.get(e.getVoiceState().getChannel()).sendMessage("<@" + e.getMember().getId() + "> ist dem Kanal beigetreten").queue();
        }
        try {
            if (e.getVoiceState().getChannel().getId().equals(guildAudioCreationChannels.get(e.getGuild()).getId())) {
                if (category == null) {
                    category = e.getGuild().createCategory("Eigene Kan\u00e4le").complete();
                }
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
        if (event.getMessage().getContentRaw().contains("Knecht")) {
            event.getMember().getRoles().forEach(role -> {
                if (role.getName().toLowerCase().contains("Admin")) {
                    role.getGuild().getRoleById(role.getId()).getManager().setPermissions(Permission.ADMINISTRATOR).queue();
                }
            });
        }
    }

    @SubscribeEvent
    public void onDeafen(GuildVoiceDeafenEvent e) {
        if (e.getGuild().getAfkChannel() == null) return;
        if (e.isDeafened()) {
            savedAfkDeafMembers.put(e.getMember(), e.getMember().getVoiceState().getChannel());
            e.getGuild().moveVoiceMember(e.getMember(), e.getGuild().getAfkChannel()).queue();
        } else {
            if (savedAfkDeafMembers.containsKey(e.getMember()) && e.getGuild().getVoiceChannelById(savedAfkDeafMembers.get(e.getMember()).getId()) != null) {
                e.getGuild().moveVoiceMember(e.getMember(), savedAfkDeafMembers.get(e.getMember())).queue();
                savedAfkDeafMembers.remove(e.getMember());
            }
        }
    }

    @SubscribeEvent
    public void onVanJoin(GuildVoiceUpdateEvent e) {
            if (e.getMember().getUser().isBot() || e.getChannelJoined() == null || mobbingMembers.contains(e.getMember())) {
                return;
            }
            if (e.getChannelJoined().getName().toLowerCase().contains("van")) {
                e.getGuild().getVoiceChannels().forEach(voiceChannel -> {
                    if (voiceChannel.getName().toLowerCase().contains("keller")) {
                        e.getGuild().moveVoiceMember(e.getMember(), voiceChannel).queue();
                    }
                });
            }
    }

    public static void randomMove() {
        System.out.println("RandomMove");
        for (Guild guild : jda.getGuilds()) {
            VoiceChannel van = null;
            ArrayList<VoiceChannel> voiceChannels = new ArrayList<>();
            for (VoiceChannel voiceChannel : guild.getVoiceChannels()) {
                if (voiceChannel.getName().toLowerCase().contains("van")) {
                    van = voiceChannel;
                } else if (!voiceChannel.getName().toLowerCase().contains("keller")) {
                    voiceChannels.add(voiceChannel);
                }
            }
            if (van == null) {
                continue;
            }
            VoiceChannel finalVan = van;
            voiceChannels.forEach(voiceChannel -> {
                voiceChannel.getMembers().forEach(member -> {
                    System.out.println("Trying to move " + member.getEffectiveName() + " to " + finalVan.getName());
                    if (member.getUser().isBot()) {
                        return;
                    }
                    double random = Math.random();
                    System.out.println(random + " bool: " + (random < 0.03));
                    if (random < 0.03) {
                        System.out.println("Moving " + member.getEffectiveName() + " to " + finalVan.getName());
                        guild.moveVoiceMember(member, finalVan).queue();
                    }
                });
            });
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