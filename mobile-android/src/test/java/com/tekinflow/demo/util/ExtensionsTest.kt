package com.tekinflow.demo.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExtensionsTest {

    @Test
    fun `toFormattedDateTime formats timestamp in Turkish style`() {
        assertThat(0L.toFormattedDateTime()).isEqualTo("01.01.1970 02:00")
    }

    @Test
    fun `toFormattedDate formats timestamp in Turkish style`() {
        assertThat(0L.toFormattedDate()).isEqualTo("01.01.1970")
    }

    @Test
    fun `toFormattedTime formats only time`() {
        assertThat(0L.toFormattedTime()).isEqualTo("02:00")
    }

    @Test
    fun `orDefault returns fallback for null`() {
        assertThat((null as String?).orDefault()).isEqualTo("-")
    }

    @Test
    fun `orDefault returns fallback for blank string`() {
        assertThat("   ".orDefault("varsayilan")).isEqualTo("varsayilan")
    }

    @Test
    fun `orDefault returns original value when not blank`() {
        assertThat("TekinFlow".orDefault()).isEqualTo("TekinFlow")
    }
}
