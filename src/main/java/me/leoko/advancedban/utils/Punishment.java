package me.leoko.advancedban.utils;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.TimeManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.RandomStringUtils;

/**
 * Created by Leoko @ dev.skamps.eu on 30.05.2016.
 */
public class Punishment {
    private static final MethodInterface mi = Universal.get().getMethods();
    private final String name, uuid, operator, calculation;
    private final long start, end;
    private final PunishmentType type;

    private String reason;
    private int id;

    public Punishment(String name, String uuid, String reason, String operator, PunishmentType type, long start, long end, String calculation, int id) {
        this.name = name;
        this.uuid = uuid;
        this.reason = reason;
        this.operator = operator;
        this.type = type;
        this.start = start;
        this.end = end;
        this.calculation = calculation;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getReason() {
        return (reason == null ? mi.getString(mi.getConfig(), "DefaultReason", "none") : reason).replaceAll("'", "");
    }

    public String getOperator() {
        return operator;
    }

    public String getCalculation() {
        return calculation;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public PunishmentType getType() {
        return type;
    }

    public int getId() {
        return id;
    }
    
    public String getHexId() {
    	return Integer.toHexString(id);
    }
    
    public String randomHexId() {
    	return RandomStringUtils.randomAlphanumeric(8).toUpperCase();
    }
    
    public String getDate (long date) {
    	SimpleDateFormat format = new SimpleDateFormat(mi.getString(mi.getConfig(), "DateFormat", "dd.MM.yyyy-HH:mm"));
    	return format.format(new Date(date));
    }

    public void create(){
        create(false);
    }

    public void create(boolean silent) {
        if (id != -1) {
            System.out.println("!! Failed! HypixelBan tried to overwrite the punishment:");
            System.out.println("!! Failed at: " + toString());
            return;
        }

        if (uuid == null) {
            System.out.println("!! Failed! HypixelBan has not saved the " + getType().getName() + " because there is no fetched UUID");
            System.out.println("!! Failed at: " + toString());
            return;
        }

        DatabaseManager.get().executeStatement(SQLQuery.INSERT_PUNISHMENT_HISTORY, getName(), getUuid(), getReason(), getOperator(), getType().name(), getStart(), getEnd(), getCalculation());

        if (getType() != PunishmentType.KICK) {
            try {
                DatabaseManager.get().executeStatement(SQLQuery.INSERT_PUNISHMENT, getName(), getUuid(), getReason(), getOperator(), getType().name(), getStart(), getEnd(), getCalculation());
                ResultSet rs = DatabaseManager.get().executeResultStatement(SQLQuery.SELECT_EXACT_PUNISHMENT, getUuid(), getStart());
                if (rs.next()) {
                    id = rs.getInt("id");
                } else {
                    System.out.println("!! No able to update ID of punishment! Please restart the server to resolve this issue!");
                    System.out.println("!! Failed at: " + toString());
                }
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        final int cWarnings = getType().getBasic() == PunishmentType.WARNING ? (PunishmentManager.get().getCurrentWarns(getUuid()) + 1) : 0;

        if (getType().getBasic() == PunishmentType.WARNING) {
            String cmd = "";
            for (int i = 1; i <= cWarnings; i++) {
                if (mi.contains(mi.getConfig(), "WarnActions." + i)) {
                    cmd = mi.getString(mi.getConfig(), "WarnActions." + i);
                }
            }
            final String finalCmd = cmd.replaceAll("%PLAYER%", getName()).replaceAll("%COUNT%", cWarnings + "").replaceAll("%REASON%", getReason());
            mi.runSync(() -> {
                mi.executeCommand(finalCmd);
                System.out.println("[HypixelBan] Executing command: " + finalCmd);
            });
        }

        if(!silent)
            announce(cWarnings);

        if (mi.isOnline(getName())) {
            final Object p = mi.getPlayer(getName());

            if (getType().getBasic() == PunishmentType.BAN || getType() == PunishmentType.KICK) {
                mi.runSync(() -> mi.kickPlayer(getName(), getLayoutBSN()));
            } else {
                for (String str : getLayout()) {
                    mi.sendMessage(p, str);
                }
                PunishmentManager.get().getLoadedPunishments(false).add(this);
            }
        }

        PunishmentManager.get().getLoadedHistory().add(this);

        mi.callPunishmentEvent(this);
    }

    public void updateReason(String reason){
        this.reason = reason;

        if (id != -1) {
            DatabaseManager.get().executeStatement(SQLQuery.UPDATE_PUNISHMENT_REASON, reason, id);
        }
    }

    private void announce(int cWarnings){
        List<String> notification = MessageManager.getLayout(mi.getMessages(),
                getType().getConfSection() + ".Notification",
                "OPERATOR", getOperator(),
                "PREFIX", MessageManager.getMessage("General.Prefix"),
                "DURATION", getDuration(true),
                "REASON", getReason(),
                "NAME", getName(),
                "ID", String.valueOf(getId()),
                "HEXID", getHexId(),
                "RANDOMID", randomHexId(),
                "DATE", getDate(start),
                "COUNT", cWarnings + "");

        mi.notify("hypixelban." + getType().getName() + ".notify", notification);
    }

    public void delete() {
        delete(false);
    }

    public void delete(boolean massClear) {
        if (getType() == PunishmentType.KICK) {
            System.out.println("!! Failed deleting! You are not able to delete Kicks!");
        }

        if (id == -1) {
            System.out.println("!! Failed deleting! The Punishment is not created yet!");
            System.out.println("!! Failed at: " + toString());
            return;
        }

        DatabaseManager.get().executeStatement(SQLQuery.DELETE_PUNISHMENT, getId());

        PunishmentManager.get().getLoadedPunishments(false).remove(this);

        mi.callRevokePunishmentEvent(this, massClear);
    }

    public List<String> getLayout() {
        boolean isLayout = getReason().matches("@.+") || getReason().matches("~.+");

        return MessageManager.getLayout(
                isLayout ? mi.getLayouts() : mi.getMessages(),
                isLayout ? "Message." + getReason().substring(1) : getType().getConfSection() + ".Layout",
                "OPERATOR", getOperator(),
                "PREFIX", MessageManager.getMessage("General.Prefix"),
                "DURATION", getDuration(false),
                "REASON", getReason(),
                "HEXID", getHexId(),
                "RANDOMID", randomHexId(),
                "DATE", getDate(start),
                "COUNT", getType().getBasic() == PunishmentType.WARNING ? (PunishmentManager.get().getCurrentWarns(getUuid()) + 1) + "" : "0",
        		"ID", String.valueOf(getId()));
    }

    public String getDuration(boolean fromStart) {
        String duration = "permanent";
        if (getType().isTemp()) {
            long diff = (getEnd() - (fromStart ? start : TimeManager.getTime())) / 1000;
            if (diff > 60 * 60 * 24) {
                duration = MessageManager.getMessage("General.TimeLayoutD", "D", diff / 60 / 60 / 24 + "", "H", diff / 60 / 60 % 24 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + "");
            } else if (diff > 60 * 60) {
                duration = MessageManager.getMessage("General.TimeLayoutH", "H", diff / 60 / 60 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + "");
            } else if (diff > 60) {
                duration = MessageManager.getMessage("General.TimeLayoutM", "M", diff / 60 + "", "S", diff % 60 + "");
            } else {
                duration = MessageManager.getMessage("General.TimeLayoutS", "S", diff + "");
            }
        }
        return duration;
    }

    public String getLayoutBSN() {
        StringBuilder msg = new StringBuilder();
        for (String str : getLayout()) {
            msg.append("\n").append(str);
        }
        return msg.substring(1);
    }

    public boolean isExpired() {
        return getType().isTemp() && getEnd() <= TimeManager.getTime();
    }

    @Override
    public String toString() {
        return "Punishment{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", uuid='" + uuid + '\'' +
                ", reason='" + reason + '\'' +
                ", operator='" + operator + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", calculation='" + calculation + '\'' +
                ", type=" + type +
                '}';
    }
}