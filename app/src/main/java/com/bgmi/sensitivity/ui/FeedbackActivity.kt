package com.bgmi.sensitivity.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.ApiClient
import com.bgmi.sensitivity.data.ApiException
import com.bgmi.sensitivity.databinding.ActivityFeedbackBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Optional feedback form: "did this recommendation feel right?".
 *
 * Ratings are stored server-side and are the input for retuning the formula.
 */
class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private var submitted = false

    /** Labels shown in the dropdown, paired with the value sent to the API. */
    private val scopeOptions: List<Pair<String, String>> by lazy {
        listOf(
            getString(R.string.feedback_scope_overall) to "overall",
            getString(R.string.section_camera) to "camera",
            getString(R.string.row_red_dot_tpp) to "red_dot_2x",
            getString(R.string.row_3x) to "3x",
            getString(R.string.row_4x) to "4x",
            getString(R.string.row_6x) to "6x",
            getString(R.string.row_8x) to "8x",
            getString(R.string.row_ads) to "ads",
            getString(R.string.section_gyroscope) to "gyroscope",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val model = intent.getStringExtra(EXTRA_MODEL).orEmpty()
        val phoneId = intent.getIntExtra(EXTRA_PHONE_ID, -1).takeIf { it >= 0 }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textModel.text = model

        binding.inputScope.setSimpleItems(scopeOptions.map { it.first }.toTypedArray())
        binding.inputScope.setText(scopeOptions.first().first, false)

        binding.buttonSubmit.setOnClickListener { submit(model, phoneId) }
    }

    private fun submit(model: String, phoneId: Int?) {
        val rating = binding.ratingBar.rating.roundToInt()
        if (rating <= 0) {
            Snackbar.make(binding.root, R.string.error_rating_required, Snackbar.LENGTH_SHORT).show()
            return
        }

        val selectedLabel = binding.inputScope.text?.toString().orEmpty()
        val scope = scopeOptions.firstOrNull { it.first == selectedLabel }?.second ?: "overall"
        val comment = binding.inputComment.text?.toString()

        lifecycleScope.launch {
            setLoading(true)
            try {
                ApiClient.submitFeedback(
                    phoneId = phoneId,
                    model = model,
                    rating = rating,
                    scope = scope,
                    comment = comment,
                )
                Snackbar.make(binding.root, R.string.feedback_thanks, Snackbar.LENGTH_SHORT).show()
                submitted = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (api: ApiException) {
                showError(api.message ?: getString(R.string.error_title))
            } catch (e: Exception) {
                showError(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        // Stay disabled after a successful submission so one tap is one vote.
        binding.buttonSubmit.isEnabled = !loading && !submitted
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_dismiss, null)
            .show()
    }

    companion object {
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_PHONE_ID = "phone_id"

        fun intent(context: Context, model: String, phoneId: Int?): Intent =
            Intent(context, FeedbackActivity::class.java)
                .putExtra(EXTRA_MODEL, model)
                .putExtra(EXTRA_PHONE_ID, phoneId ?: -1)
    }
}
