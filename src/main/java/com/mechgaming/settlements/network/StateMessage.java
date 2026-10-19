package com.mechgaming.settlements.network;

import com.mechgaming.settlements.Settlements;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class StateMessage implements IMessage {
    private NBTTagCompound data;
    public StateMessage() { }
    public StateMessage(NBTTagCompound data) { this.data = data; }
    @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
    public static class Handler implements IMessageHandler<StateMessage, IMessage> {
        @Override public IMessage onMessage(StateMessage message, MessageContext context) {
            // FML supplies the receiving side's thread without common code importing Minecraft.
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                if (message.data != null) Settlements.PROXY.acceptState(message.data);
            });
            return null;
        }
    }
}
