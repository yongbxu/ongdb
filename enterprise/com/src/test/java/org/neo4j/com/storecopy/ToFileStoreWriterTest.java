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
package org.neo4j.com.storecopy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.neo4j.com.DataProducer;
import org.neo4j.kernel.impl.store.StoreType;
import org.neo4j.test.rule.PageCacheRule;
import org.neo4j.test.rule.TestDirectory;
import org.neo4j.test.rule.fs.EphemeralFileSystemRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToFileStoreWriterTest
{
    private final EphemeralFileSystemRule fs = new EphemeralFileSystemRule();
    private final TestDirectory directory = TestDirectory.testDirectory( fs );
    private final PageCacheRule pageCacheRule = new PageCacheRule();

    @Rule
    public final RuleChain rules = RuleChain.outerRule( fs ).around( directory ).around( pageCacheRule );

    @Test
    public void shouldLetPageCacheHandleRecordStoresAndNativeLabelScanStoreFiles() throws Exception
    {
        // GIVEN
        ToFileStoreWriter writer = new ToFileStoreWriter( directory.databaseDir(), fs,
                new StoreCopyClientMonitor.Adapter() );
        ByteBuffer tempBuffer = ByteBuffer.allocate( 128 );

        // WHEN
        for ( StoreType type : StoreType.values() )
        {
            if ( type.isRecordStore() )
            {
                File[] files = directory.databaseLayout().file( type.getDatabaseFile() ).toArray( File[]::new );
                for ( File file : files )
                {
                    writeAndVerify( writer, tempBuffer, file );
                }
            }
        }
        writeAndVerify( writer, tempBuffer, directory.databaseLayout().labelScanStore() );
    }

    @Test
    public void fullPathFileNamesUsedForMonitoringBackup() throws IOException
    {
        // given
        AtomicBoolean wasActivated = new AtomicBoolean( false );
        StoreCopyClientMonitor monitor = new StoreCopyClientMonitor.Adapter()
        {
            @Override
            public void startReceivingStoreFile( String file )
            {
                assertTrue( file.contains( "expectedFileName" ) );
                wasActivated.set( true );
            }
        };

        // and
        ToFileStoreWriter writer = new ToFileStoreWriter( directory.absolutePath(), fs,
                monitor );
        ByteBuffer tempBuffer = ByteBuffer.allocate( 128 );

        // when
        writer.write( "expectedFileName", new DataProducer( 16 ), tempBuffer, true, 16 );

        // then
        assertTrue( wasActivated.get() );
    }

    private void writeAndVerify( ToFileStoreWriter writer, ByteBuffer tempBuffer, File file )
            throws IOException
    {
        writer.write( file.getName(), new DataProducer( 16 ), tempBuffer, true, 16 );
        assertTrue( "File created by writer should exist." , fs.fileExists( file ) );
        assertEquals( 16, fs.getFileSize( file ) );
    }
}
