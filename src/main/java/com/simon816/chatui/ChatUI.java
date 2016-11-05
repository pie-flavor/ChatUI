package com.simon816.chatui;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.simon816.chatui.channel.WrapOutputChannel;
import com.simon816.chatui.pagination.TabbedPaginationService;
import com.simon816.chatui.privmsg.PrivateMessageFeature;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.filter.IsCancelled;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.action.ClickAction;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.util.Tristate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Plugin(id = "chatui", name = "Chat UI")
public class ChatUI {

    public static final String ADMIN_PERMISSON = "chatui.admin";

    private final Map<UUID, PlayerChatView> playerViewMap = Maps.newHashMap();
    private final List<AbstractFeature> features = Lists.newArrayList();

    private static ChatUI instance;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> confLoader;

    @Inject
    private Logger logger;

    public static ChatUI instance() {
        return instance;
    }

    public static PlayerChatView getView(CommandSource source) {
        checkArgument(source instanceof Player);
        return getView((Player) source);
    }

    public static PlayerChatView getView(Player player) {
        return getView(player.getUniqueId());
    }

    public static PlayerChatView getView(UUID uuid) {
        return instance.playerViewMap.get(uuid);
    }

    public static ClickAction<?> command(String subcommand) {
        return TextActions.runCommand("/chatui " + subcommand);
    }

    @Listener
    public void onPreInit(GamePreInitializationEvent event) {
        instance = this;
    }

    @Listener
    public void onInit(GameInitializationEvent event) {
        Sponge.getGame().getCommandManager().register(this, new ChatUICommand(), "chatui");
        Config.init(this.confLoader, this.logger);
        this.addFeature(new PrivateMessageFeature());
    }

    public void addFeature(AbstractFeature feature) {
        this.features.add(feature);
        feature.onInit();
    }

    @Listener(order = Order.POST)
    public void onPostInit(GamePostInitializationEvent event) {
        Optional<ProviderRegistration<PaginationService>> optService = Sponge.getGame().getServiceManager().getRegistration(PaginationService.class);
        if (!optService.isPresent()) {
            return;
        }
        PaginationService service = optService.get().getProvider();
        Sponge.getGame().getServiceManager().setProvider(this, PaginationService.class, new TabbedPaginationService(service));
    }

    @Listener
    public void onGameReload(GameReloadEvent event) {
        Config.loadConfig();
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        initialize(event.getTargetEntity());
    }

    void initialize(Player player) {
        ConfigurationNode playerSettings = Config.playerConfig(player.getUniqueId());
        PlayerChatView view;
        if (!playerSettings.getNode("enabled").getBoolean()) {
            view = new DisabledChatView(player);
        } else {
            view = new ActivePlayerChatView(player, playerSettings);
        }
        this.playerViewMap.put(player.getUniqueId(), view);
        view.update();
    }

    @Listener
    public void onPlayerQuit(ClientConnectionEvent.Disconnect event) {
        PlayerChatView view = this.playerViewMap.remove(event.getTargetEntity().getUniqueId());
        Config.saveConfig();
        for (AbstractFeature feature : this.features) {
            feature.onViewClose(view);
        }
        view.getWindow().closeAll();
        // TODO Offline message buffering?
    }

    // Pretty aggressively captures incoming chat
    @Listener(order = Order.PRE, beforeModifications = true)
    @IsCancelled(Tristate.UNDEFINED)
    public void onIncomingMessage(MessageChannelEvent.Chat event, @Root Player player) {
        if (getView(player).handleIncoming(event.getRawMessage())) {
            // No plugins should interpret this as chat because the processing
            // is for tab-specific behaviour
            event.setCancelled(true);
            event.setChannel(MessageChannel.TO_NONE);
        }
    }

    // Try to be last so we can wrap around any messages sent
    @Listener(order = Order.POST)
    public void onOutgoingMessage(MessageChannelEvent event) {
        if (!event.getChannel().isPresent() || event.isMessageCancelled()) {
            return;
        }
        CommandSource source = null;
        Object rootCause = event.getCause().root();
        if (rootCause instanceof CommandSource) {
            source = (CommandSource) rootCause;
        }
        event.setChannel(new WrapOutputChannel(event.getChannel().get(), source));
    }

    void loadFeatures(PlayerChatView view) {
        for (AbstractFeature feature : this.features) {
            feature.onNewPlayerView(view);
        }
    }

}
