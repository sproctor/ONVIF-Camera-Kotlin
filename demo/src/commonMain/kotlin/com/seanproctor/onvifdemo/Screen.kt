package com.seanproctor.onvifdemo

sealed interface Screen {
    data object Main : Screen
    data class CameraConnect(val camera: CameraInformation) : Screen
    data object CameraDetails : Screen
    data object Snapshot : Screen
    data object Stream : Screen
}