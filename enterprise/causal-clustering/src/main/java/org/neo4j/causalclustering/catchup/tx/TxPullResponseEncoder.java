/*
 * Copyright (c) 2018-2020 "Graph Foundation"
 * Graph Foundation, Inc. [https://graphfoundation.org]
 *
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of ONgDB Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) as found
 * in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
package org.neo4j.causalclustering.catchup.tx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import org.neo4j.causalclustering.messaging.NetworkWritableChannel;
import org.neo4j.causalclustering.messaging.marshalling.storeid.StoreIdMarshal;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryWriter;

public class TxPullResponseEncoder extends MessageToByteEncoder<TxPullResponse>
{
    @Override
    protected void encode( ChannelHandlerContext ctx, TxPullResponse response, ByteBuf out ) throws Exception
    {
        NetworkWritableChannel channel = new NetworkWritableChannel( out );
        StoreIdMarshal.INSTANCE.marshal( response.storeId(), channel );
        new LogEntryWriter( channel ).serialize( response.tx() );
    }
}
