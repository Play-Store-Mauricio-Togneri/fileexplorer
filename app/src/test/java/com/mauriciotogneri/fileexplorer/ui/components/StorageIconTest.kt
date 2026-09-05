package com.mauriciotogneri.fileexplorer.ui.components

import com.mauriciotogneri.fileexplorer.data.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The icon is what tells the two kinds of volume apart at a glance on the home screen, the
 * analyzer and the destination picker, which all draw it from here. Asserted on the vector's name
 * because the icons carry no content description — they label nothing the volume's name does not
 * already say, so a screen reader announcing them would only repeat it.
 */
class StorageIconTest {

    @Test
    fun `each kind of volume draws its own icon`() {
        assertEquals("Outlined.PhoneAndroid", storageIcon(StorageType.INTERNAL).name)
        assertEquals("Outlined.SdCard", storageIcon(StorageType.SD_CARD).name)
    }
}
