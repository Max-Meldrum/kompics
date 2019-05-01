/* 
 * This file is part of the Kompics component model runtime.
 *
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) 
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.kompics.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.slf4j.MDC;
import se.sics.kompics.network.MessageNotify;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.netty.serialization.Serializers;

/**
 *
 * @author Lars Kroll {@literal <lkroll@kth.se>}
 */
public class MessageEncoder extends MessageToMessageEncoder<MessageWrapper> {

    private static final byte[] LENGTH_PLACEHOLDER = new byte[2];

    private final NettyNetwork component;

    public MessageEncoder(NettyNetwork component) {
        this.component = component;
    }

    // @Override
    // protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
    // NettyNetwork.LOG.trace("Trying to encode outgoing data to {} from {}.", ctx.channel().remoteAddress(),
    // ctx.channel().localAddress());
    // int startIdx = out.writerIndex();
    // out.writeBytes(LENGTH_PLACEHOLDER);
    //
    // Serializers.toBinary(msg, out);
    //
    // int endIdx = out.writerIndex();
    // int diff = endIdx - startIdx - LENGTH_PLACEHOLDER.length;
    // if (diff > 65532) { //2^16 - 2bytes for the length header (snappy wants no more than 65536 bytes uncompressed)
    // throw new Exception("Can't encode message longer than 65532 bytes!");
    // }
    // out.setShort(startIdx, diff);
    // NettyNetwork.LOG.trace("Encoded outgoing {} bytes of data to {}: {}.", new Object[]{diff,
    // ctx.channel().remoteAddress(), ByteBufUtil.hexDump(out)});
    // }
    @Override
    protected void encode(ChannelHandlerContext ctx, MessageWrapper msgw, List<Object> outL) throws Exception {
        component.setCustomMDC();
        try {

            long startTS = System.nanoTime(); // start measuring here to avoid overestimating the throughput
            Msg<?, ?> msg = msgw.msg;
            ByteBuf out = ctx.alloc().buffer(NettyNetwork.INITIAL_BUFFER_SIZE, NettyNetwork.SEND_BUFFER_SIZE);
            component.extLog.trace("Trying to encode outgoing data to {} from {}: {}.", ctx.channel().remoteAddress(),
                    ctx.channel().localAddress(), msg.getClass());
            int startIdx = out.writerIndex();
            out.writeBytes(LENGTH_PLACEHOLDER);

            try {
                if (msgw.notify.isPresent() && msgw.notify.get().notifyOfDelivery) {
                    MessageNotify.Req msgr = msgw.notify.get();
                    component.extLog.trace("Serialising message with AckRequest: {}", msgr.getMsgId());
                    AckRequestMsg arm = new AckRequestMsg(msg, msgr.getMsgId());
                    Serializers.toBinary(arm, out);
                } else {
                    Serializers.toBinary(msg, out);
                }
            } catch (Throwable e) {
                component.extLog.warn("There was a problem serialising {}: \n --> {}", msgw, e);
                e.printStackTrace(System.err);
                throw e;
            }

            int endIdx = out.writerIndex();
            int diff = endIdx - startIdx - LENGTH_PLACEHOLDER.length;
            if (diff > 65532) { // 2^16 - 2bytes for the length header (snappy wants no more than 65536 bytes
                                // uncompressed)
                throw new Exception("Can't encode message longer than 65532 bytes!");
            }
            out.setShort(startIdx, diff);
            // component.LOG.trace("Encoded outgoing {} bytes of data to {}: {}.", new Object[]{diff,
            // ctx.channel().remoteAddress(), ByteBufUtil.hexDump(out)});
            msgw.injectSize(diff, startTS);
            outL.add(out);
        } finally {
            MDC.clear();
        }
    }

}
