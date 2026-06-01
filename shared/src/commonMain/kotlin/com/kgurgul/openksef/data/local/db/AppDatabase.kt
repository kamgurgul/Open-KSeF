/*
 * Copyright KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.openksef.data.local.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

const val DATABASE_FILE_NAME = "openksef.db"

@Database(
    entities = [InvoiceTemplateEntity::class, SellerConfigEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(InvoiceTemplateItemConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceTemplateDao(): InvoiceTemplateDao

    abstract fun sellerConfigDao(): SellerConfigDao
}

// The compiler generates the `actual` implementations per platform.
@Suppress(
    "KotlinNoActualForExpect",
    "NO_ACTUAL_FOR_EXPECT",
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA",
)
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Platform specific [RoomDatabase.Builder] pointing at the on-disk database file. */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
