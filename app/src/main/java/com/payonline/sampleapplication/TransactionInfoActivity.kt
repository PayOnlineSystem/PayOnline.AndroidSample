package com.payonline.sampleapplication

import android.content.Context
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.payonline.sampleapplication.helpers.AndroidHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.progressBar
import kotlinx.android.synthetic.main.activity_transaction_info.*
import sdk.*
import java.io.IOException

class TransactionInfoActivity : AppCompatActivity() {

    private var request: PayRequest? = null
    private var response: PayResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_info)

        request = intent.getSerializableExtra(IntentParameterNames.TRANSACTION_REQUEST) as? PayRequest
        response = intent.getSerializableExtra(IntentParameterNames.TRANSACTION_RESPONSE) as? PayResponse

        fillInfoFromIntent()

        fiscalButton.setOnClickListener{
            fiscalButton.isClickable = false
            progressBar.visibility = View.VISIBLE
            FiscalApiAsync().execute("")
        }
    }

    private fun fillInfoFromIntent(){
        amountIdTextView.text = request?.amount.toString()
        statusTextView.text = response?.status.toString()
        rebillAnchorTextView.text = response?.rebillAnchor
        orderIdTextView.text = request?.orderId
    }

    private fun createFiscalRequest(): FiscalRequest {
        return FiscalRequest(merchantId = Constants.MERCHANT_ID,
            privateSecurityKey =  Constants.PRIVATE_SECURITY_KEY,
            total = request?.amount?:0.0,
            operation = FiscalOperation.Benefit,
            paymentSystemType = FiscalPaymentSystemType.card,
            transactionId = response?.id.toString(),
            items = listOf(FiscalItem("Test item", 1, FiscalTax.none, request?.amount?:0.0)),
            clientPhone = "89564875468",
            email = "test@test.dsf")
    }

    private fun goMessageDialog(title: String, message: String){
        AndroidHelper().makeMessageDialog(this,title, message)
    }

    inner class FiscalApiAsync() : AsyncTask<String, String, FiscalResponse>() {

        private var exception: java.lang.Exception? = null
        private lateinit var context: Context
        public fun initContext(context:Context){
            this.context = context
        }



        override fun doInBackground(vararg urls: String): FiscalResponse {
            try {

                var client = PaymentClient(Constants.HOST)
                return client.createFiscalPayload(createFiscalRequest())

            } catch (e: IOException) {
                throw PaymentClientException("Error when calling service")
            }
        }

        override fun onPostExecute(result: FiscalResponse) {
            // TODO: check this.exception
            // TODO: do something with the feed
            if(result != null && result.payload != null){
                //openWebView(result.acsUrl.toString(), md ,pareq, termUrl)
                AndroidHelper().makeMessageDialog(context,"Fiscal success", Gson().toJson(result))
                fiscalButton.visibility = View.VISIBLE
            }
            else{
                goMessageDialog("Fiscal Error", result?.status?.text?.toString()?:"Something went wrong")
            }

            progressBar.visibility = View.GONE
            fiscalButton.isClickable = true
        }
    }
}
