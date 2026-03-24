package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestPermissionDeclarationTest {

    @Test
    fun `toBaselineString with protectionLevel`() {
        val decl = ManifestPermissionDeclaration(name = "com.example.CUSTOM", protectionLevel = "signature")
        assertThat(decl.toBaselineString()).isEqualTo("com.example.CUSTOM (protectionLevel=signature)")
    }

    @Test
    fun `toBaselineString without protectionLevel`() {
        val decl = ManifestPermissionDeclaration(name = "com.example.NORMAL", protectionLevel = null)
        assertThat(decl.toBaselineString()).isEqualTo("com.example.NORMAL")
    }

    @Test
    fun `toBaselineString with dangerous protectionLevel`() {
        val decl = ManifestPermissionDeclaration(name = "com.example.DANGEROUS", protectionLevel = "dangerous")
        assertThat(decl.toBaselineString()).isEqualTo("com.example.DANGEROUS (protectionLevel=dangerous)")
    }
}
