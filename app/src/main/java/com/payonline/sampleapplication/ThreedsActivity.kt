package com.payonline.sampleapplication


import android.content.Intent
import android.net.http.SslError
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.android.synthetic.main.activity_threeds.*
import java.nio.charset.Charset
import com.payonline.sampleapplication.models.ThreedsData
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_transaction_info.*
import kotlinx.android.synthetic.main.activity_transaction_info.progressBar
import sdk.*
import java.io.IOException
import android.webkit.JavascriptInterface
import org.jsoup.Jsoup
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.appcompat.app.AlertDialog
import com.payonline.sampleapplication.helpers.AndroidHelper
import java.lang.StringBuilder


class ThreedsActivity : AppCompatActivity() {

    private lateinit var threedsData: ThreedsData
    private lateinit var transactionResponse: PayResponse
    private lateinit var transactionRequest: PayRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_threeds)

        threedsData = intent.getSerializableExtra(IntentParameterNames.THREEDS_DATA) as ThreedsData
        transactionResponse = intent.getSerializableExtra(IntentParameterNames.TRANSACTION_RESPONSE) as PayResponse
        transactionRequest = intent.getSerializableExtra(IntentParameterNames.TRANSACTION_REQUEST) as PayRequest

        configureWebView(threedsData)
    }

    private fun configureWebView(threedsData: ThreedsData) {
        val set = webView.getSettings()
        set.setJavaScriptEnabled(true)
        set.javaScriptCanOpenWindowsAutomatically = true
        set.setBuiltInZoomControls(true)
        set.domStorageEnabled = true;
        set.allowContentAccess = true
        webView.addJavascriptInterface(JavaScriptInterface(), "JavaScriptThreeDs")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.i("urllll", url)
                super.onPageFinished(webView, url)
                if(url.equals(Constants.TERM_URL)){
                    webView.loadUrl("javascript:window.JavaScriptThreeDs.processHTML('<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');");
                }
            }
        }

        webView.loadData(generateFormDataFor3ds(), "text/html", "utf-8")
    }

    private fun generateFormDataFor3ds(): String{
        var sb = StringBuilder()

        sb.appendln("<form id=\"threedsForm\" action=\"${threedsData.acsUrl}\" method=\"POST\">")
        sb.appendln("<input type=\"hidden\" name=\"PaReq\" value=\"${threedsData.pareq}\">")
        sb.appendln("<input type=\"hidden\" name=\"MD\" value=\"${threedsData.md}\">")
        sb.appendln("<input type=\"hidden\" name=\"TermUrl\" value=\"${threedsData.termUrl}\">")
        sb.appendln("<input type=\"submit\" value=\"Send\">")
        sb.appendln("</form>")

        sb.appendln("<script>")
        sb.appendln("document.getElementById('threedsForm').submit();")
        sb.appendln("</script>")

        return sb.toString()
    }

    private fun createThreedsRequest(pares:String): Process3DsRequest{
        return Process3DsRequest(Constants.MERCHANT_ID,
            Constants.PRIVATE_SECURITY_KEY,
            transactionResponse.id,
            pares,
            transactionResponse.pd.toString())
    }

    inner class JavaScriptInterface {
        @JavascriptInterface
        fun processHTML(html: String) {

            val doc = Jsoup.parse(html)
            val element = doc.select("body").first()

            val paRes = element.select("input[name=PARes]").attr("Value").toString()
            ThreeDsApiAsync().execute(paRes)
        }
    }

    private fun toTransactionInfo(request: PayRequest, response: PayResponse){
        val intent = Intent(this, TransactionInfoActivity::class.java)
        intent.putExtra(IntentParameterNames.TRANSACTION_REQUEST, request)
        intent.putExtra(IntentParameterNames.TRANSACTION_RESPONSE, response)
        startActivity(intent)
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

    override fun onDestroy() {
        webView.removeJavascriptInterface("JavaScriptThreeDs")
        super.onDestroy()
    }

    inner class ThreeDsApiAsync : AsyncTask<String, String, Process3DsResponse>() {

        private var exception: java.lang.Exception? = null

        override fun doInBackground(vararg params: String): Process3DsResponse {
            try {

                var client = PaymentClient(Constants.HOST)
                return client.process3Ds(createThreedsRequest(params[0]))

            } catch (e: IOException) {
                throw PaymentClientException("Error when calling service")
            }
        }

        override fun onPostExecute(result: Process3DsResponse) {
            // TODO: check this.exception
            // TODO: do something with the feed
            if(result != null && result.code == 200){
                toTransactionInfo(transactionRequest, result)
                finish()
            }
            else{
                alertDialogWork(result)
            }

            /*progressBar.visibility = View.GONE
            fiscalButton.isClickable = true*/
        }
    }
}
