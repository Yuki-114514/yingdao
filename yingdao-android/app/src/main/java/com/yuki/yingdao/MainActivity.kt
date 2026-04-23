package com.yuki.yingdao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yuki.yingdao.ui.YingDaoApp
import com.yuki.yingdao.ui.theme.YingDaoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YingDaoTheme {
                YingDaoApp()
            }
        }
    }
}

