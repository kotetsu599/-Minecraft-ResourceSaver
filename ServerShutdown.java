package kotetsu.serverShutdown;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.event.EventHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServerShutdown extends Plugin implements Listener {

    private static final String FLASK_HOST = "IP";
    private static final int FLASK_PORT = 8080;
    private static final String PASSWORD = "flaskのパスワード";

    private static final String CREATIVE_SERVER_NAME = "creative";


    private volatile long creativeEmptySince = -1;//creativeEmptySinceが-1以外の場合は、オンライン状態かつサーバーに誰もいない

    private volatile boolean creativeServerStarting = false;

    private static final long SHUTDOWN_DELAY = TimeUnit.MINUTES.toMillis(5);

    private ScheduledTask checkTask;

    private ScheduledTask joinTask;

    private final List<String> playersTriedToConnect = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onEnable() {
        getLogger().info("クリエイティブサーバーを管理します！");
        getProxy().getPluginManager().registerListener(this, this);
    }

    private void checkCreativeServerStatus() {
        ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(CREATIVE_SERVER_NAME);
        int playerCount = serverInfo.getPlayers().size();

        if (playerCount == 0) {
            if (creativeEmptySince == -1) { // 最初に空になったとき
                creativeEmptySince = System.currentTimeMillis();
                getLogger().info("creativeサーバーが空になりました。このままでは5分後にサーバーが停止します。");
            }

            long elapsed = System.currentTimeMillis() - creativeEmptySince;
            if (elapsed >= SHUTDOWN_DELAY) {
                getLogger().info("creativeサーバーが5分間空だったため、shutdownリクエストを送信します。");
                getProxy().getScheduler().runAsync(this, () -> sendFlaskRequest("shutdown"));
                checkTask.cancel();
                checkTask = null;
                creativeEmptySince = -1;
            }
        } else {
            creativeEmptySince = -1;
        }
    }

    private void joinPlayerAutomatically() {
        ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(CREATIVE_SERVER_NAME);
        if (serverInfo == null) return;

        if (isServerOnline(CREATIVE_SERVER_NAME)) {
            synchronized (playersTriedToConnect) {
                Iterator<String> iterator = playersTriedToConnect.iterator();
                while (iterator.hasNext()) {
                    String playerName = iterator.next();
                    ProxiedPlayer player = getProxy().getPlayer(playerName);
                    if (player != null) {
                        player.connect(serverInfo);
                    }
                    iterator.remove();
                }
            }
            creativeEmptySince = -1;
        }

        synchronized (playersTriedToConnect) {
            if (playersTriedToConnect.isEmpty() && joinTask != null) {
                joinTask.cancel();
                joinTask = null;
            }
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ServerInfo target = event.getTarget();
        if (target != null && target.getName().equalsIgnoreCase(CREATIVE_SERVER_NAME)) {

            if (checkTask == null) {
                checkTask = getProxy().getScheduler().schedule(this, this::checkCreativeServerStatus, 10, 10, TimeUnit.SECONDS);
            }
            if (isServerOnline(CREATIVE_SERVER_NAME)) {
                creativeEmptySince = -1;
                return;
            }

            synchronized (playersTriedToConnect) {
                playersTriedToConnect.add(event.getPlayer().getName());
            }
            event.setCancelled(true);
            ProxiedPlayer player = event.getPlayer();
            sendStartupTitle(player);

            if (!creativeServerStarting) {
                creativeServerStarting = true;
                getLogger().info("プレイヤー " + player.getName() + " が creativeサーバーに参加しようとしています。/start リクエストを送信します。");
                getProxy().getScheduler().runAsync(this, () -> sendFlaskRequest("start"));

                getProxy().getScheduler().schedule(this, () -> creativeServerStarting = false, 30, TimeUnit.SECONDS);
            }

            if (joinTask == null) {
                joinTask = getProxy().getScheduler().schedule(this, this::joinPlayerAutomatically, 5, 5, TimeUnit.SECONDS);
            }
        }
    }


    private void sendFlaskRequest(String action) {
        String urlString = String.format("http://%s:%d/%s?password=%s", FLASK_HOST, FLASK_PORT, action, PASSWORD);
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            getLogger().info(String.format("Flask の '%s' エンドポイントにリクエストを送信しました (HTTP %d)", action, responseCode));

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                getLogger().info(response.toString());
            }

        } catch (Exception e) {
            getLogger().severe("Flask へのリクエスト送信中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendStartupTitle(ProxiedPlayer player) {
        Title title = ProxyServer.getInstance().createTitle()
                .title(new TextComponent("§aサーバーを起動中です"))
                .subTitle(new TextComponent("§e自動的に転送します..."))
                .fadeIn(20)    // 約1秒
                .stay(600)     // 約30秒
                .fadeOut(20);  // 約1秒
        player.sendTitle(title);
    }

    /**
     *ソケット接続により、指定サーバーがオンラインかどうかを判定
     *一部のサーバープロバイダーではうまく機能しないかもしれないです。
     */
    private boolean isServerOnline(String serverName) {
        ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(serverName);
        if (serverInfo == null) {
            return false;
        }

        String host = serverInfo.getAddress().getHostName();
        int port = serverInfo.getAddress().getPort();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
