package com.mechgaming.settlements.network;

import com.mechgaming.settlements.common.SettlementService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** A request only: authority, costs, permissions and distances are checked on the server. */
public class ClientAction implements IMessage {
    public String action = "";
    public BlockPos pos = BlockPos.ORIGIN;
    public int value;
    public String text = "";
    public NBTTagCompound payload = new NBTTagCompound();

    public ClientAction() { }
    public ClientAction(String action, BlockPos pos, int value, String text, NBTTagCompound payload) {
        this.action = action;
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.value = value;
        this.text = text == null ? "" : text;
        this.payload = payload == null ? new NBTTagCompound() : payload;
    }
    @Override public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() > 16384) throw new IllegalArgumentException("Settlements request too large");
        action = ByteBufUtils.readUTF8String(buf);
        pos = BlockPos.fromLong(buf.readLong());
        value = buf.readInt();
        text = ByteBufUtils.readUTF8String(buf);
        payload = ByteBufUtils.readTag(buf);
        if (action.length() > 64 || text.length() > 256) throw new IllegalArgumentException("Invalid Settlements request");
        if (payload == null) payload = new NBTTagCompound();
    }
    @Override public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, action);
        buf.writeLong(pos.toLong());
        buf.writeInt(value);
        ByteBufUtils.writeUTF8String(buf, text);
        ByteBufUtils.writeTag(buf, payload);
    }
    public static class Handler implements IMessageHandler<ClientAction, IMessage> {
        @Override public IMessage onMessage(ClientAction message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.connection != null && !player.isDead && player.getServer() != null
                        && player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) == player) {
                    SettlementService.handle(player, message.action, message.pos, message.value, message.text, message.payload);
                }
            });
            return null;
        }
    }
}
