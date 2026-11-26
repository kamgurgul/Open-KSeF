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

package com.kgurgul.openksef.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankAccountTest {

    @Test
    fun normalizeNumber_removesSeparatorsAndUppercases() {
        assertEquals(
            "PL61109010140000071219812874",
            BankAccount.normalizeNumber(" pl61 1090-1014 0000 0712 1981 2874 "),
        )
    }

    @Test
    fun isValidNumber_acceptsPolishNrbWithValidChecksum() {
        assertTrue(BankAccount.isValidNumber("61 1090 1014 0000 0712 1981 2874"))
    }

    @Test
    fun isValidNumber_rejectsPolishNrbWithTypo() {
        assertFalse(BankAccount.isValidNumber("61 1090 1014 0000 0712 1981 2875"))
    }

    @Test
    fun isValidNumber_validatesIbanChecksum() {
        assertTrue(BankAccount.isValidNumber("PL61 1090 1014 0000 0712 1981 2874"))
        assertTrue(BankAccount.isValidNumber("DE89 3704 0044 0532 0130 00"))
        assertTrue(BankAccount.isValidNumber("IE29 AIBK 9311 5212 3456 78"))
        assertFalse(BankAccount.isValidNumber("DE89 3704 0044 0532 0130 01"))
    }

    @Test
    fun isValidNumber_acceptsNonIbanAccountsWithinLengthLimits() {
        assertTrue(BankAccount.isValidNumber("0123456789"))
        assertFalse(BankAccount.isValidNumber("012345678"))
        assertFalse(BankAccount.isValidNumber("1".repeat(35)))
    }

    @Test
    fun isValidNumber_rejectsSpecialCharacters() {
        assertFalse(BankAccount.isValidNumber("1234567890/12"))
    }

    @Test
    fun isValidSwift_acceptsEightAndElevenCharacterCodes() {
        assertTrue(BankAccount.isValidSwift("BPKOPLPW"))
        assertTrue(BankAccount.isValidSwift("bpko plpw xxx"))
        assertFalse(BankAccount.isValidSwift("BPKOPL"))
        assertFalse(BankAccount.isValidSwift("BPKOPLPWX"))
        assertFalse(BankAccount.isValidSwift("1PKOPLPW"))
    }

    @Test
    fun normalizeSwift_removesSpacesAndUppercases() {
        assertEquals("BPKOPLPWXXX", BankAccount.normalizeSwift(" bpko plpw xxx "))
    }
}
