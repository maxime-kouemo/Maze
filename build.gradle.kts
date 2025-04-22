// Top-level build file where you can add configuration options common to all sub-projects/modules.
@file:Suppress("suppressKotlinVersionCompatibilityCheck")

plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.googleDaggerHiltAndroid) apply false
    alias(libs.plugins.googleDevtoolsKsp) apply false
}