package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Part-name grammar. The potted sapling is the one Japanese name that does not follow
 * "〈樹種〉の〈部位〉": vanilla says {@code 植えられたオークの苗木}, so the generator has to copy that
 * word order or the name reads as a pot made of saplings. Pure strings, no game bootstrap.
 */
class WoodLanguagesTest {

    @Test
    void japanesePottedSaplingFollowsVanillaWordOrder() {
        assertEquals("植えられたオークの苗木",
                WoodLanguages.partName("オーク", WoodPart.POTTED_SAPLING, false, WoodLanguages.JA_JP),
                "vanilla block.minecraft.potted_oak_sapling");
        assertEquals("植えられたメープルの苗木",
                WoodLanguages.partName("メープル", WoodPart.POTTED_SAPLING, false, WoodLanguages.JA_JP));
    }

    @Test
    void pottedSaplingIsTheOnlyPartWithThatPrefix() {
        assertEquals("メープルの苗木",
                WoodLanguages.partName("メープル", WoodPart.SAPLING, false, WoodLanguages.JA_JP));
        assertEquals("Potted Maple Sapling",
                WoodLanguages.partName("Maple", WoodPart.POTTED_SAPLING, false, WoodLanguages.EN_US));
    }

    @Test
    void wallSignNameIsNotTheStandingName() {
        assertEquals("メープルの看板",
                WoodLanguages.partName("メープル", WoodPart.SIGN, false, WoodLanguages.JA_JP));
        assertEquals("壁に付けられたメープルの看板",
                WoodLanguages.partName("メープル", WoodPart.SIGN, true, WoodLanguages.JA_JP));
        assertNotEquals(
                WoodLanguages.partName("Maple", WoodPart.SIGN, false, WoodLanguages.EN_US),
                WoodLanguages.partName("Maple", WoodPart.SIGN, true, WoodLanguages.EN_US),
                "the wall sign needs its own name, not the standing sign's");
    }
}
