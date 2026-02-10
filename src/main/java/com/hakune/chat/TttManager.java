package com.hakune.chat;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TttManager {
    private final HakuneChatPlugin plugin;
    private final Map<UUID, Invite> pending = new ConcurrentHashMap<>(); // target -> invite
    private final Map<UUID, Game> games = new ConcurrentHashMap<>(); // player -> game

    public TttManager(HakuneChatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInGame(UUID player) {
        return games.containsKey(player);
    }

    public boolean sendInvite(Player sender, Player target, long expiresAtMillis) {
        if (isInGame(sender.getUniqueId()) || isInGame(target.getUniqueId())) {
            return false;
        }
        pending.put(target.getUniqueId(), new Invite(sender.getUniqueId(), expiresAtMillis));
        return true;
    }

    public boolean hasInvite(Player target, Player sender) {
        Invite invite = pending.get(target.getUniqueId());
        return invite != null && sender.getUniqueId().equals(invite.sender);
    }

    public boolean acceptInvite(Player target, Player sender) {
        Invite invite = pending.get(target.getUniqueId());
        if (invite == null || !invite.sender.equals(sender.getUniqueId())) {
            return false;
        }
        if (invite.isExpired()) {
            pending.remove(target.getUniqueId());
            return false;
        }
        pending.remove(target.getUniqueId());
        Game game = new Game(sender.getUniqueId(), target.getUniqueId());
        games.put(sender.getUniqueId(), game);
        games.put(target.getUniqueId(), game);
        return true;
    }

    public boolean declineInvite(Player target, Player sender) {
        Invite invite = pending.get(target.getUniqueId());
        if (invite == null || !invite.sender.equals(sender.getUniqueId())) {
            return false;
        }
        pending.remove(target.getUniqueId());
        return true;
    }

    public void removeInvitesFor(UUID player) {
        pending.remove(player);
        pending.entrySet().removeIf(e -> e.getValue().sender.equals(player));
    }

    public Invite getInvite(UUID target) {
        return pending.get(target);
    }

    public void removeInvite(UUID target) {
        pending.remove(target);
    }

    public Game getGame(UUID player) {
        return games.get(player);
    }

    public void endGame(Game game) {
        games.remove(game.x);
        games.remove(game.o);
    }

    public boolean makeMove(Player player, int pos) {
        Game game = games.get(player.getUniqueId());
        if (game == null) {
            return false;
        }
        if (pos < 1 || pos > 9) {
            return false;
        }
        return game.makeMove(player.getUniqueId(), pos - 1);
    }

    public void sendBoard(Game game) {
        Player x = Bukkit.getPlayer(game.x);
        Player o = Bukkit.getPlayer(game.o);
        if (x != null) {
            sendBoardTo(x, game);
        }
        if (o != null) {
            sendBoardTo(o, game);
        }
    }

    public void sendBoardTo(Player viewer, Game game) {
        boolean bedrock = plugin.getBedrockDetector().isBedrock(viewer.getUniqueId());
        if (bedrock) {
            viewer.sendMessage(renderBoardPlain(game));
            viewer.sendMessage(Component.text("Use /ttt move <1-9>"));
            return;
        }
        viewer.sendMessage(renderBoardClickable(game, viewer.getUniqueId()));
    }

    private Component renderBoardClickable(Game game, UUID viewer) {
        Component result = Component.text("Tic Tac Toe").color(NamedTextColor.AQUA);
        result = result.append(Component.newline());
        for (int row = 0; row < 3; row++) {
            Component line = Component.empty();
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                char c = game.board[idx];
                Component cell = renderCell(c, idx, viewer, game);
                line = line.append(cell);
                if (col < 2) {
                    line = line.append(Component.text(" | ").color(NamedTextColor.GRAY));
                }
            }
            result = result.append(line);
            if (row < 2) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    private Component renderCell(char c, int idx, UUID viewer, Game game) {
        if (c == 'X') {
            return Component.text("X").color(NamedTextColor.GREEN);
        }
        if (c == 'O') {
            return Component.text("O").color(NamedTextColor.RED);
        }
        boolean myTurn = game.isTurn(viewer);
        Component base = Component.text(Integer.toString(idx + 1)).color(NamedTextColor.YELLOW);
        if (!myTurn) {
            return base.color(NamedTextColor.DARK_GRAY);
        }
        return base.clickEvent(ClickEvent.runCommand("/ttt move " + (idx + 1)))
            .hoverEvent(HoverEvent.showText(Component.text("Move to " + (idx + 1))));
    }

    private Component renderBoardPlain(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tic Tac Toe\n");
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                char c = game.board[idx];
                sb.append(c == ' ' ? (idx + 1) : c);
                if (col < 2) {
                    sb.append(" | ");
                }
            }
            if (row < 2) {
                sb.append("\n");
            }
        }
        return Component.text(sb.toString());
    }

    public static final class Game {
        final UUID x;
        final UUID o;
        private final char[] board = new char[9];
        private UUID turn;

        private Game(UUID x, UUID o) {
            this.x = x;
            this.o = o;
            Arrays.fill(board, ' ');
            this.turn = x;
        }

        boolean isTurn(UUID player) {
            return turn.equals(player);
        }

        boolean makeMove(UUID player, int idx) {
            if (!isTurn(player)) {
                return false;
            }
            if (board[idx] != ' ') {
                return false;
            }
            board[idx] = player.equals(x) ? 'X' : 'O';
            turn = player.equals(x) ? o : x;
            return true;
        }

        boolean isWin(char c) {
            int[][] wins = {
                {0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},
                {0,4,8},{2,4,6}
            };
            for (int[] w : wins) {
                if (board[w[0]] == c && board[w[1]] == c && board[w[2]] == c) {
                    return true;
                }
            }
            return false;
        }

        boolean isDraw() {
            for (char c : board) {
                if (c == ' ') {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class Invite {
        final UUID sender;
        final long expiresAt;

        private Invite(UUID sender, long expiresAt) {
            this.sender = sender;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
