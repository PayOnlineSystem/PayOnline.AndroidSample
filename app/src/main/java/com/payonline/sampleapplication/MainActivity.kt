package com.payonline.sampleapplication

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.*
import com.payonline.sampleapplication.googlepay.PaymentsUtil
import com.payonline.sampleapplication.googlepay.microsToString
import com.payonline.sampleapplication.helpers.AndroidHelper
import com.payonline.sampleapplication.models.ThreedsData
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import sdk.*
import java.io.IOException
import java.lang.StringBuilder
import kotlin.math.roundToLong
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var paymentsClient: PaymentsClient
    private val LOAD_PAYMENT_DATA_REQUEST_CODE = 991
    private val shippingCost = (2).toLong()
    private lateinit var garmentList: JSONArray
    private lateinit var selectedGarment: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        paymentsClient = PaymentsUtil.createPaymentsClient(this)
        possiblyShowGooglePayButton()

        payButton.setOnClickListener{
            if(validateRequest()){
                progressBar.visibility = View.VISIBLE
                CallApiAsync().execute("")
            }
        }
    }

    private fun possiblyShowGooglePayButton() {

        val isReadyToPayJson = PaymentsUtil.isReadyToPayRequest() ?: return
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString()) ?: return

        // The call to isReadyToPay is asynchronous and returns a Task. We need to provide an
        // OnCompleteListener to be triggered when the result of the call is known.
        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                completedTask.getResult(ApiException::class.java)?.let(::setGooglePayAvailable)
            } catch (exception: ApiException) {
                // Process error
                Log.w("isReadyToPay failed", exception)
            }
        }
    }

    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            googlePayButton.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                this,
                "Unfortunately, Google Pay is not available on this device",
                Toast.LENGTH_LONG).show();
        }
    }

    private fun requestPayment() {

        // Disables the button to prevent multiple clicks.
        googlePayButton.isClickable = false

        val price = amountEditText.text.toString().toDouble().roundToLong().microsToString()

        val paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(price)
        if (paymentDataRequestJson == null) {
            Log.e("RequestPayment", "Can't fetch payment data request")
            return
        }
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())

        // Since loadPaymentData may show the UI asking the user to select a payment method, we use
        // AutoResolveHelper to wait for the user interacting with it. Once completed,
        // onActivityResult will be called with the result.
        if (request != null) {
            AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            // value passed in AutoResolveHelper
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK ->
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)

                            val paymentInformation = PaymentData.getFromIntent(intent)?.toJson() ?: return
                            val paymentMethodData = JSONObject(paymentInformation).getJSONObject("paymentMethodData")

                            progressBar.visibility = View.VISIBLE
                            var result = CallApiAsync().execute(paymentMethodData
                                .getJSONObject("tokenizationData")
                                .getString("token"))

                        }
                    Activity.RESULT_CANCELED -> {
                        // Nothing to do here normally - the user simply cancelled without selecting a
                        // payment method.
                    }

                    AutoResolveHelper.RESULT_ERROR -> {
                        AutoResolveHelper.getStatusFromIntent(data)?.let {
                            handleError(it.statusCode)
                        }
                    }
                }
                // Re-enables the Google Pay payment button.
                googlePayButton.isClickable = true
            }
        }
    }

    private fun handleError(statusCode: Int) {
        Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode))
    }

    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return

        try {
            // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
            val paymentMethodData = JSONObject(paymentInformation).getJSONObject("paymentMethodData")

            // If the gateway is set to "example", no payment information is returned - instead, the
            // token will only consist of "examplePaymentMethodToken".
            if (paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("type") == "PAYMENT_GATEWAY" && paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("token") == "examplePaymentMethodToken") {

                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Gateway name set to \"example\" - please modify " +
                            "Constants.java and replace it with your own gateway.")
                    .setPositiveButton("OK", null)
                    .create()
                    .show()
            }

            val billingName = paymentMethodData.getJSONObject("info")
                .getJSONObject("billingAddress").getString("name")
            Log.d("BillingName", billingName)

            // Logging token string.
            Log.d("GooglePaymentToken", paymentMethodData
                .getJSONObject("tokenizationData")
                .getString("token"))

        } catch (e: JSONException) {
            Log.e("handlePaymentSuccess", "Error: " + e.toString())
        }

    }

    private fun validateRequest():Boolean{
        val sb = StringBuilder();
        if(amountEditText.text.toString().toDoubleOrNull() == null){
            sb.appendln("Invalid amount")
        }
        if(cardHolderEditText.text.toString().isNullOrEmpty()){
            sb.appendln("CardHolder is required")
        }
        if(cvvEditText.text.toString().isNullOrEmpty()){
            sb.appendln("CVV is required")
        }
        if(cardNumberEditText.text.toString().isNullOrEmpty()){
            sb.appendln("Card number is required")
        }
        if(monthEditText.text.toString().toIntOrNull() == null){
            sb.appendln("Invalid month")
        }
        if(yearEditText.text.toString().toIntOrNull() == null){
            sb.appendln("Invalid year")
        }

        var message = sb.toString()

        AndroidHelper().makeMessageDialog(this, "Invalid request", message)

        return message.isNullOrEmpty()
    }

    private fun alertDialogWork(result: PayResponse) {
        if(result != null){
            var message = "Something went wrong"
            if(result.code == 200) {
                return
            }
            else if(result.code == 6004){
                message = "3D-Secure unavailable"
            }
            else{
                message = result.message ?: "Something went wrong"

            }

            AndroidHelper().makeMessageDialog(this, result.code.toString(), message)

        }    else {
            AndroidHelper().makeMessageDialog(this,"Oops", "Something went wrong")
        }
    }

    private fun createRequest(): PayRequest {
        return PayRequest(merchantId = Constants.MERCHANT_ID,
            privateSecurityKey =  Constants.PRIVATE_SECURITY_KEY,
            amount = amountEditText.text.toString().toDouble(),
            cardHolderName = cardHolderEditText.text.toString(),
            cardCvv = cvvEditText.text.toString(),
            cardNumber = cardNumberEditText.text.toString(),
            month = monthEditText.text.toString().toInt(),
            year = yearEditText.text.toString().toInt(),
            orderId = Random.nextInt(20000, 90000).toString(),
            currency = currencyEditText.text.toString());
    }

    private fun toTransactionInfo(request: PayRequest, response: PayResponse){
        val intent = Intent(this, TransactionInfoActivity::class.java)
        intent.putExtra("REQUEST", request)
        intent.putExtra("RESPONSE", response)
        startActivity(intent)
    }

    private fun toThreedsForm(request: PayRequest, response: PayResponse){
        var pareq = response.paReq.toString()
        var md = response.id.toString() +";"+ response.pd.toString()
        var termUrl = "https://payonline.ru"

        val intent = Intent(this, ThreedsActivity::class.java)
        intent.putExtra(IntentParameterNames.THREEDS_DATA, ThreedsData(response.acsUrl.toString(), pareq, md, termUrl))
        intent.putExtra(IntentParameterNames.TRANSACTION_RESPONSE, response)
        intent.putExtra(IntentParameterNames.TRANSACTION_REQUEST, request)

        startActivity(intent)
    }

    inner class CallApiAsync : AsyncTask<String, String, PayResponse>() {

        private var exception: java.lang.Exception? = null

        override fun doInBackground(vararg urls: String): PayResponse {
            try {

                var client = PaymentClient(Constants.HOST)
                var request = createRequest()
                return client.pay(request)
            } catch (e: IOException) {
                throw PaymentClientException("Error when calling service")
            }
        }

        override fun onPostExecute(result: PayResponse) {
            // TODO: check this.exception
            // TODO: do something with the feed
            var request = createRequest();
            if(result.acsUrl != null && result.acsUrl != ""){
                toThreedsForm(request,result)
            }
            else{
                alertDialogWork(result)
            }
            if(result.code == 200){
                toTransactionInfo(request, result)
            }

            progressBar.visibility = View.GONE
            payButton.isClickable = true
        }
    }

    inner class GooglePayApiAsync : AsyncTask<String, String, PayResponse>() {

        private var exception: java.lang.Exception? = null

        override fun doInBackground(vararg urls: String): PayResponse {
            try {

                var client = PaymentClient(Constants.HOST)
                var request = createGooglePayRequest(urls[0])
                return client.googlePay(request)
            } catch (e: IOException) {
                throw PaymentClientException("Error when calling service")
            }
        }

        override fun onPostExecute(result: PayResponse) {
            // TODO: check this.exception
            // TODO: do something with the feed
            var request = createRequest();
            if(result.acsUrl != null && result.acsUrl != ""){
                toThreedsForm(request,result)
            }
            else{
                alertDialogWork(result)
            }
            if(result.code == 200){
                toTransactionInfo(request, result)
            }

            progressBar.visibility = View.GONE
            payButton.isClickable = true
        }
    }

    private fun createGooglePayRequest(token: String): GooglePayRequest {
        var requestForGoogle = GooglePayRequest();
        requestForGoogle.merchantId = Constants.MERCHANT_ID;
        requestForGoogle.privateSecurityKey = Constants.PRIVATE_SECURITY_KEY
        requestForGoogle.setAmount(2.00);
        requestForGoogle.orderId = Random.nextInt(900000).toString();
        requestForGoogle.googleMerchantId = "12345678901234567890";
        requestForGoogle.currency = "RUB"

        requestForGoogle.googlePaymentToken = token

        Log.i("Token",requestForGoogle.googlePaymentToken)

        return requestForGoogle
    }


}
