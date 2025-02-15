package com.connect.demo.controller.manage

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.LogUtils
import com.connect.common.ConnectCallback
import com.connect.common.IConnectAdapter
import com.connect.common.SignCallback
import com.connect.common.model.Account
import com.connect.common.model.ConnectError
import com.connect.common.utils.PrefUtils
import com.connect.demo.R
import com.connect.demo.base.BaseActivity
import com.connect.demo.controller.secret.ImportWalletActivity
import com.connect.demo.databinding.ActivityManageBinding
import com.connect.demo.utils.ChainUtils
import com.connect.demo.utils.MockManger
import com.connect.demo.utils.toast
import com.evm.adapter.EVMConnectAdapter
import com.particle.base.model.LoginType
import com.particle.connect.ParticleConnect
import com.phantom.adapter.PhantomConnectAdapter
import com.solana.adapter.SolanaConnectAdapter
import com.wallet.connect.adapter.TrustConnectAdapter
import com.wallet.connect.adapter.WalletConnectAdapter
import network.particle.chains.ChainInfo
import particle.auth.adapter.ParticleConnectConfig

class ManageActivity : BaseActivity<ActivityManageBinding>(R.layout.activity_manage) {

    private val adapter = ConnectAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupRv()
        setupToolbar()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        setSubTitle()
    }

    lateinit var chainInfo: ChainInfo
    private fun setSubTitle() {
        val selectChain = PrefUtils.getSettingInt("current_selected_chain", 1)
        chainInfo = ChainUtils.getAllChains()[selectChain]
        val name = chainInfo.fullname
        binding.toolbar.subtitle = "Current Chain: $name"
    }

    private fun setupRv() {
        binding.adapterRv.layoutManager = LinearLayoutManager(this)
        binding.adapterRv.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
        binding.adapterRv.setHasFixedSize(true)

        adapter.setOnItemClickListener { _, _, position ->
            val connectAdapter = adapter.data[position]
            connectWallet(connectAdapter)
        }
        binding.adapterRv.adapter = adapter
        adapter.setList(ParticleConnect.getAdapters())
    }

    private var qrDialog: WalletConnectFragment? = null
    private fun connectWallet(connectAdapter: IConnectAdapter) {
        when (connectAdapter) {
            is SolanaConnectAdapter -> {
                showImportMenu(connectAdapter, "solana")
            }

            is EVMConnectAdapter -> {
                showImportMenu(connectAdapter, "evm")
            }

            is WalletConnectAdapter -> {
                connectAdapter.connect(null, object : ConnectCallback {
                    override fun onConnected(account: Account) {
                        toast("connect success")
                        qrDialog?.dismissAllowingStateLoss()
                        finish()
                    }

                    override fun onError(error: ConnectError) {
                        toast(error.message)
                        qrDialog?.dismissAllowingStateLoss()
                    }
                })
                val url = connectAdapter.qrCodeUri()
                url?.let {
                    qrDialog = WalletConnectFragment.show(supportFragmentManager, it)
                }
            }

            else -> {
                if (connectCheck(connectAdapter)) {
                    return
                }
                val config = ParticleConnectConfig(LoginType.PHONE)
                connectAdapter.connect(config, object : ConnectCallback {
                    override fun onConnected(account: Account) {
                        LogUtils.d("connect success account: $account")
                        toast("connect success")
                        finish()
                    }

                    override fun onError(error: ConnectError) {
                        LogUtils.d("connect error: $error")
                        toast(error.message)
                    }
                })
            }
        }
    }

    private fun connectCheck(connectAdapter: IConnectAdapter): Boolean {

        if (connectAdapter is PhantomConnectAdapter) {
            if (chainInfo.isEvmChain()) {
                toast("Phantom only support Solana Chain,current is ${chainInfo.name} ${chainInfo.id}")
                return true
            }
        }
        if (connectAdapter is TrustConnectAdapter) {
            return if (chainInfo.isEvmChain()) {
                if (chainInfo.isMainnet()) {
                    false
                } else {
                    toast("Trust only support EVM Mainnet Chain,current is ${chainInfo.name} ${chainInfo.id}")
                    true
                }
            } else {
                toast("Trust only support EVM Chain,current is ${chainInfo.name} ${chainInfo.id}")
                true
            }
        }
        return false
    }

    private fun showImportMenu(connectAdapter: IConnectAdapter, chainType: String) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setIcon(R.drawable.ic_logo)
        alertDialog.setTitle("Particle Connect")

        alertDialog.setItems(
            arrayOf(
                "Import ${chainType.uppercase()} Wallet",
                "Create ${chainType.uppercase()} Wallet"
            )
        ) { _, which ->
            if (which == 0) {
                val intent = Intent(this, ImportWalletActivity::class.java)
                intent.putExtra("chainType", chainType)
                startActivity(intent)
            } else {
                createWallet(connectAdapter)
            }
        }

        alertDialog.setNegativeButton("Cancel", null)
        alertDialog.create().show()
    }

    private fun createWallet(connectAdapter: IConnectAdapter) {
        connectAdapter.connect(null, object : ConnectCallback {
            override fun onConnected(account: Account) {
                toast("Create wallet success")
                finish()
            }

            override fun onError(error: ConnectError) {
                toast(error.message)
            }
        })
    }
}