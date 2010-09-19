/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3.0 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package goldengate.commandexec.client;

import goldengate.commandexec.utils.LocalExecDefaultResult;
import goldengate.commandexec.utils.LocalExecResult;
import goldengate.common.future.GgFuture;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * Handles a client-side channel.
 *
 *
 */
public class LocalExecClientHandler extends SimpleChannelUpstreamHandler {

    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(LocalExecClientHandler.class);

    private StringBuilder back;

    private LocalExecResult result = new LocalExecResult(LocalExecDefaultResult.NoStatus);
    private boolean firstMessage = true;

    private GgFuture future;

    public LocalExecClientHandler() {
        this.back = new StringBuilder();
        this.future = new GgFuture(true);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelClosed(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        logger.debug("ChannelClosed");
        if (firstMessage) {
            logger.warn(result.status+" "+result.result);
            result.set(LocalExecDefaultResult.NoMessage);
        } else {
            result.result = back.toString();
        }
        if (result.status < 0) {
            if (result.exception != null) {
                this.future.setFailure(result.exception);
            } else {
                this.future.cancel();
            }
        } else {
            this.future.setSuccess();
        }
        super.channelClosed(ctx, e);
    }

    // Waiting for the close of the exec
    public LocalExecResult waitFor() {
        this.future.awaitUninterruptibly();
        result.isSuccess = this.future.isSuccess();
        return result;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        // Add the line received from the server.
        String mesg = (String) e.getMessage();
        logger.debug("Recv: "+mesg);
        if (firstMessage) {
            firstMessage = false;
            int pos = mesg.indexOf(' ');
            result.status = Integer.parseInt(mesg.substring(0, pos));
            mesg = mesg.substring(pos+1);
            result.result = mesg;
        }
        back.append(mesg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.error("Unexpected exception from downstream.", e.getCause());
        if (firstMessage) {
            firstMessage = false;
            result.set(LocalExecDefaultResult.BadTransmition);
            result.exception = (Exception) e.getCause();
            back = new StringBuilder("Error in LocalExec: ");
            back.append(result.exception.getMessage());
            back.append('\n');
        } else {
            back.append("\nERROR while receiving answer: ");
            result.exception = (Exception) e.getCause();
            back.append(result.exception.getMessage());
            back.append('\n');
        }
        e.getChannel().close();
    }
}