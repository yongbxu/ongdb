/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) as found
 * in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.causalclustering.protocol.handshake;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * General status codes sent in responses.
 */
public enum StatusCode
{
    SUCCESS( 0 ),
    ONGOING( 1 ),
    FAILURE( -1 );

    private final int codeValue;
    private static AtomicReference<Map<Integer, StatusCode>> codeMap = new AtomicReference<>();

    StatusCode( int codeValue )
    {
        this.codeValue = codeValue;
    }

    public int codeValue()
    {
        return codeValue;
    }

    public static Optional<StatusCode> fromCodeValue( int codeValue )
    {
        Map<Integer,StatusCode> map = codeMap.get();
        if ( map == null )
        {
             map = Stream.of( StatusCode.values() )
                    .collect( Collectors.toMap( StatusCode::codeValue, Function.identity() ) );

            codeMap.compareAndSet( null, map );
        }
        return Optional.ofNullable( map.get( codeValue ) );
    }
}
