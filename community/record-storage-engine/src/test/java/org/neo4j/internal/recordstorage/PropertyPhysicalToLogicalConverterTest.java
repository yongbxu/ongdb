/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.recordstorage;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.neo4j.configuration.Config;
import org.neo4j.internal.id.DefaultIdGeneratorFactory;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.PropertyStore;
import org.neo4j.kernel.impl.store.StoreFactory;
import org.neo4j.kernel.impl.store.record.NodeRecord;
import org.neo4j.kernel.impl.store.record.PropertyBlock;
import org.neo4j.kernel.impl.store.record.PropertyRecord;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.storageengine.api.EntityUpdates;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.rule.TestDirectory;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PageCacheExtension
class PropertyPhysicalToLogicalConverterTest
{
    @Inject
    private PageCache pageCache;
    @Inject
    private FileSystemAbstraction fs;
    @Inject
    private TestDirectory testDirectory;

    private NeoStores neoStores;
    private PropertyStore store;
    private final Value longString = Values.of( "my super looooooooooooooooooooooooooooooooooooooong striiiiiiiiiiiiiiiiiiiiiiing" );
    private final Value longerString = Values.of( "my super looooooooooooooooooooooooooooooooooooooong striiiiiiiiiiiiiiiiiiiiiiingdd" );
    private PropertyPhysicalToLogicalConverter converter;
    private final long[] none = new long[0];

    @BeforeEach
    void before()
    {
        StoreFactory storeFactory = new StoreFactory( testDirectory.databaseLayout(), Config.defaults(), new DefaultIdGeneratorFactory( fs ),
                pageCache, fs, NullLogProvider.getInstance() );
        neoStores = storeFactory.openAllNeoStores( true );
        store = neoStores.getPropertyStore();
        converter = new PropertyPhysicalToLogicalConverter( store );
    }

    @AfterEach
    void after()
    {
        neoStores.close();
    }

    @Test
    void shouldConvertInlinedAddedProperty()
    {
        // GIVEN
        int key = 10;
        Value value = Values.of( 12345 );
        PropertyRecord before = propertyRecord();
        PropertyRecord after = propertyRecord( property( key, value ) );

        // WHEN
        MatcherAssert.assertThat(
                convert( none, none, change( before, after ) ),
                equalTo( EntityUpdates.forEntity( 0, false ).added( key, value ).build() ) );
    }

    @Test
    void shouldConvertInlinedChangedProperty()
    {
        // GIVEN
        int key = 10;
        Value valueBefore = Values.of( 12341 );
        Value valueAfter = Values.of( 738 );
        PropertyRecord before = propertyRecord( property( key, valueBefore ) );
        PropertyRecord after = propertyRecord( property( key, valueAfter ) );

        // WHEN
        EntityUpdates update = convert( none, none, change( before, after ) );

        // THEN
        EntityUpdates expected = EntityUpdates.forEntity( 0, false ).changed( key, valueBefore, valueAfter ).build();
        assertEquals( expected, update );
    }

    @Test
    void shouldIgnoreInlinedUnchangedProperty()
    {
        // GIVEN
        int key = 10;
        Value value = Values.of( 12341 );
        PropertyRecord before = propertyRecord( property( key, value ) );
        PropertyRecord after = propertyRecord( property( key, value ) );

        // WHEN
        assertThat( convert( none, none, change( before, after ) ), equalTo( EntityUpdates.forEntity( 0, false ).build() ) );
    }

    @Test
    void shouldConvertInlinedRemovedProperty()
    {
        // GIVEN
        int key = 10;
        Value value = Values.of( 12341 );
        PropertyRecord before = propertyRecord( property( key, value ) );
        PropertyRecord after = propertyRecord();

        // WHEN
        EntityUpdates update = convert( none, none, change( before, after ) );

        // THEN
        EntityUpdates expected = EntityUpdates.forEntity( 0, false ).removed( key, value ).build();
        assertEquals( expected, update );
    }

    @Test
    void shouldConvertDynamicAddedProperty()
    {
        // GIVEN
        int key = 10;
        PropertyRecord before = propertyRecord();
        PropertyRecord after = propertyRecord( property( key, longString ) );

        // THEN
        assertThat( convert( none, none, change( before, after ) ), equalTo( EntityUpdates.forEntity( 0, false ).added( key, longString ).build() ) );
    }

    @Test
    void shouldConvertDynamicChangedProperty()
    {
        // GIVEN
        int key = 10;
        PropertyRecord before = propertyRecord( property( key, longString ) );
        PropertyRecord after = propertyRecord( property( key, longerString ) );

        // WHEN
        EntityUpdates update = convert( none, none, change( before, after ) );

        // THEN
        EntityUpdates expected = EntityUpdates.forEntity( 0, false ).changed( key, longString, longerString ).build();
        assertEquals( expected, update );
    }

    @Test
    void shouldConvertDynamicInlinedRemovedProperty()
    {
        // GIVEN
        int key = 10;
        PropertyRecord before = propertyRecord( property( key, longString ) );
        PropertyRecord after = propertyRecord();

        // WHEN
        EntityUpdates update = convert( none, none, change( before, after ) );

        // THEN
        EntityUpdates expected = EntityUpdates.forEntity( 0, false ).removed( key, longString ).build();
        assertEquals( expected, update );
    }

    @Test
    void shouldTreatPropertyThatMovedToAnotherRecordAsChange()
    {
        // GIVEN
        int key = 12;
        Value oldValue = Values.of( "value1" );
        Value newValue = Values.of( "value two" );
        Command.PropertyCommand movedFrom = change( propertyRecord( property( key, oldValue ) ), propertyRecord() );
        Command.PropertyCommand movedTo = change( propertyRecord(), propertyRecord( property( key, newValue ) ) );

        // WHEN
        EntityUpdates update = convert( none, none, movedFrom, movedTo );

        // THEN
        EntityUpdates expected = EntityUpdates.forEntity( 0, false ).changed( key, oldValue, newValue ).build();
        assertEquals( expected, update );
    }

    private static PropertyRecord propertyRecord( PropertyBlock... propertyBlocks )
    {
        PropertyRecord record = new PropertyRecord( 0 );
        if ( propertyBlocks != null )
        {
            record.setInUse( true );
            for ( PropertyBlock propertyBlock : propertyBlocks )
            {
                record.addPropertyBlock( propertyBlock );
            }
        }
        record.setNodeId( 0 );
        return record;
    }

    private PropertyBlock property( long key, Value value )
    {
        PropertyBlock block = new PropertyBlock();
        store.encodeValue( block, (int) key, value );
        return block;
    }

    private EntityUpdates convert( long[] labelsBefore,
            long[] labelsAfter, Command.PropertyCommand... changes )
    {
        long nodeId = 0;
        EntityUpdates.Builder updates = EntityUpdates.forEntity( (long) 0, false ).withTokens( labelsBefore ).withTokensAfter( labelsAfter );
        EntityCommandGrouper grouper = new EntityCommandGrouper<>( Command.NodeCommand.class, 8 );
        grouper.add( new Command.NodeCommand( new NodeRecord( nodeId ), new NodeRecord( nodeId ) ) );
        for ( Command.PropertyCommand change : changes )
        {
            grouper.add( change );
        }
        assertTrue( grouper.nextEntity() );
        converter.convertPropertyRecord( grouper, updates );
        return updates.build();
    }

    private Command.PropertyCommand change( final PropertyRecord before, final PropertyRecord after )
    {
        return new Command.PropertyCommand( before, after );
    }
}
