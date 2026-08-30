package com.adnanfoisal.infinitydesign.backend

import com.adnanfoisal.infinitydesign.backend.api.RequestValidator
import com.adnanfoisal.infinitydesign.backend.providers.parseBlueprint
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.generation.providers.SsrfGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SsrfGuardTest {

    @Test fun `rejects null scheme`() {
        assertThat(SsrfGuard.validate("file:///etc/passwd")).isNull()
    }

    @Test fun `rejects metadata endpoint`() {
        assertThat(SsrfGuard.validate("http://169.254.169.254/latest/meta-data/")).isNull()
    }

    @Test fun `rejects private IP`() {
        assertThat(SsrfGuard.validate("http://10.0.0.1/v1/chat")).isNull()
        assertThat(SsrfGuard.validate("http://192.168.1.1/v1/chat")).isNull()
        assertThat(SsrfGuard.validate("http://172.16.0.1/v1/chat")).isNull()
    }

    @Test fun `rejects loopback by default`() {
        assertThat(SsrfGuard.validate("http://localhost:4000/v1/chat")).isNull()
        assertThat(SsrfGuard.validate("http://127.0.0.1:4000/v1/chat")).isNull()
    }

    @Test fun `allows public hostnames`() {
        val url = "https://api.openai.com/v1/chat/completions"
        val v = SsrfGuard.validate(url)
        assertThat(v).isNotNull()
        assertThat(v).isEqualTo(url)
    }

    @Test fun `rejects malformed url`() {
        assertThat(SsrfGuard.validate("not a url")).isNull()
        assertThat(SsrfGuard.validate("")).isNull()
    }
}

class RequestValidatorTest {

    @Test fun `rejects empty prompt`() {
        val r = BlueprintRequest(
            prompt = "",
            seed = 0L,
            provider = ProviderKind.GEMINI,
            providerConfig = ProviderConfig(geminiApiKey = "test"),
        )
        val v = RequestValidator.validateBlueprintRequest(r)
        assertThat(v.isErr).isTrue()
    }

    @Test fun `accepts valid request`() {
        val r = BlueprintRequest(
            prompt = "Create a poster",
            seed = 12345L,
            provider = ProviderKind.GEMINI,
            providerConfig = ProviderConfig(
                geminiApiKey = "test-key",
                geminiModel = "gemini-3.7-flash",
            ),
        )
        val v = RequestValidator.validateBlueprintRequest(r)
        assertThat(v.isOk).isTrue()
    }

    @Test fun `rejects litellm without url`() {
        val r = BlueprintRequest(
            prompt = "Create a poster",
            seed = 0L,
            provider = ProviderKind.LITELLM,
            providerConfig = ProviderConfig(
                litellmUrl = null,
                litellmApiKey = "test",
                litellmModel = "gpt-4o",
            ),
        )
        val v = RequestValidator.validateBlueprintRequest(r)
        assertThat(v.isErr).isTrue()
    }
}

class BlueprintParsingTest {

    @Test fun `parses clean JSON`() {
        val raw = """
            {
              "id": "bp-1",
              "title": "Code Forward 2026",
              "purpose": "Hackathon poster",
              "audience": "Students",
              "mood": "Futuristic",
              "visualDirection": "Dark tech",
              "palette": {
                "name": "Dark Tech",
                "primary": "#00E5FF",
                "secondary": "#0F172A",
                "accent": "#7C4DFF",
                "neutrals": ["#1E293B"],
                "background": "#0F172A",
                "foreground": "#E0F7FA"
              },
              "typography": {
                "displayRole": "condensed-display",
                "bodyRole": "neutral-sans",
                "captionRole": "technical-mono",
                "displayWeight": 900,
                "bodyWeight": 400,
                "displayTracking": 0,
                "bodyTracking": 0
              },
              "composition": "poster-like",
              "visualLanguage": ["glow"],
              "density": "RICH",
              "texture": ["grain"],
              "decorative": [],
              "lighting": "cyan rim",
              "hierarchy": [{"role":"title","label":"Title","importance":10}],
              "semanticContent": [{"role":"title","content":"Code Forward 2026","protected":true}],
              "imagery": "abstract robotic",
              "constraints": [],
              "seed": 12345
            }
        """.trimIndent()
        val r = parseBlueprint(raw, "Create a poster", 100L)
        assertThat(r.isOk).isTrue()
        val bp = (r as AppResult.Ok).value
        assertThat(bp.blueprint.title).isEqualTo("Code Forward 2026")
        assertThat(bp.blueprint.palette.primary).isEqualTo("#00E5FF")
    }

    @Test fun `parses JSON inside markdown fence`() {
        val raw = "Sure, here you go:\n```json\n{\"id\":\"x\",\"title\":\"Y\",\"purpose\":\"\",\"audience\":\"\",\"mood\":\"\",\"visualDirection\":\"\",\"palette\":{\"name\":\"\",\"primary\":\"#000000\",\"secondary\":\"#FFFFFF\",\"accent\":\"#FF0000\",\"neutrals\":[],\"background\":\"#FFFFFF\",\"foreground\":\"#000000\"},\"typography\":{\"displayRole\":\"neutral-sans\",\"bodyRole\":\"neutral-sans\",\"captionRole\":\"neutral-sans\",\"displayWeight\":700,\"bodyWeight\":400,\"displayTracking\":0,\"bodyTracking\":0},\"composition\":\"editorial\",\"visualLanguage\":[],\"density\":\"BALANCED\",\"texture\":[],\"decorative\":[],\"lighting\":\"\",\"hierarchy\":[],\"semanticContent\":[],\"imagery\":\"\",\"constraints\":[],\"seed\":0}\n```\n"
        val r = parseBlueprint(raw, "test", 1L)
        assertThat(r.isOk).isTrue()
    }

    @Test fun `rejects empty input`() {
        val r = parseBlueprint("", "x", 1L)
        assertThat(r.isErr).isTrue()
    }

    @Test fun `rejects missing palette`() {
        val raw = """{"id":"x","title":"Y"}"""
        val r = parseBlueprint(raw, "x", 1L)
        assertThat(r.isErr).isTrue()
    }
}
