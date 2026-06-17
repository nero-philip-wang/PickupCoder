package com.example.smspicker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smspicker.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        updateSelection()
        setupButtons()
    }

    private fun setupButtons() {
        binding.btn3days.setOnClickListener { setDays(3) }
        binding.btn7days.setOnClickListener { setDays(7) }
        binding.btn14days.setOnClickListener { setDays(14) }
        binding.btn30days.setOnClickListener { setDays(30) }
    }

    private fun setDays(days: Int) {
        SmsStorage.setReadDays(days)
        updateSelection()
    }

    private fun updateSelection() {
        val days = SmsStorage.getReadDays()
        val buttons = listOf(binding.btn3days, binding.btn7days, binding.btn14days, binding.btn30days)
        val dayValues = listOf(3, 7, 14, 30)

        buttons.forEachIndexed { index, btn ->
            if (dayValues[index] == days) {
                setButtonSelected(btn)
            } else {
                setButtonUnselected(btn)
            }
        }

        binding.tvCurrentRange.text = "当前：读取 $days 天内的短信"
    }

    private fun setButtonSelected(btn: MaterialButton) {
        btn.setBackgroundColor(getColor(R.color.primary))
        btn.setTextColor(getColor(android.R.color.white))
    }

    private fun setButtonUnselected(btn: MaterialButton) {
        btn.setBackgroundColor(getColor(android.R.color.transparent))
        btn.setTextColor(getColor(R.color.text_primary))
    }
}
