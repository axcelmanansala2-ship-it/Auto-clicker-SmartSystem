package com.smartsystem.autoclicker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.smartsystem.autoclicker.databinding.ActivityAccountCheckerBinding
import com.smartsystem.autoclicker.databinding.ItemAccountBinding
import com.smartsystem.autoclicker.models.Account
import com.smartsystem.autoclicker.models.AccountRepository
import com.smartsystem.autoclicker.models.AccountStatus

class AccountCheckerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountCheckerBinding
    private lateinit var repo: AccountRepository
    private lateinit var adapter: AccountAdapter

    // 0=Pending(+InProgress), 1=Banned, 2=New, 3=Good, 4=Invalid
    private var currentTabIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountCheckerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Account Checker"
        }

        repo = AccountRepository(this)
        setupTabs()
        setupList()
        setupAddPanel()
        setupButtons()
        loadCurrentTab()
    }

    override fun onResume() {
        super.onResume()
        loadCurrentTab()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupTabs() {
        listOf("Pending", "Banned", "New Acct", "Good", "Invalid").forEach { label ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(label))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTabIdx = tab.position
                binding.addPanel.visibility = if (currentTabIdx == 0) View.VISIBLE else View.GONE
                loadCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupList() {
        adapter = AccountAdapter(emptyList()) { account -> showAccountOptions(account) }
        binding.recyclerAccounts.layoutManager = LinearLayoutManager(this)
        binding.recyclerAccounts.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recyclerAccounts.adapter = adapter
    }

    private fun setupAddPanel() {
        binding.btnAddAccounts.setOnClickListener {
            val raw = binding.etAccounts.text?.toString()?.trim() ?: ""
            if (raw.isBlank()) {
                Toast.makeText(this, "Enter accounts (user:pass per line)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val before = repo.getAll().size
            repo.parseAndAdd(raw)
            val added = repo.getAll().size - before
            binding.etAccounts.text?.clear()
            loadCurrentTab()
            Toast.makeText(this, "$added account(s) added", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        binding.btnStartChecker.setOnClickListener {
            if (!AutoClickAccessibilityService.isConnected) {
                Toast.makeText(this, "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // Reset stuck IN_PROGRESS before starting
            repo.resetInProgress()
            val pending = repo.countPending()
            if (pending == 0) {
                Toast.makeText(this, "No pending accounts to check", Toast.LENGTH_SHORT).show()
                loadCurrentTab()
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this,
                Intent(this, AccountCheckerService::class.java).apply {
                    action = AccountCheckerService.ACTION_START
                })
            Toast.makeText(this, "Checker started ($pending accounts) — check the overlay", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnStopChecker.setOnClickListener {
            startService(Intent(this, AccountCheckerService::class.java).apply {
                action = AccountCheckerService.ACTION_STOP
            })
            Toast.makeText(this, "Checker stopped", Toast.LENGTH_SHORT).show()
            loadCurrentTab()
        }

        binding.btnClearTab.setOnClickListener {
            val statusToClear = currentTabStatuses()[0]
            val label = statusToClear.name.replace('_', ' ')
            AlertDialog.Builder(this)
                .setTitle("Clear $label accounts?")
                .setPositiveButton("Clear") { _, _ ->
                    currentTabStatuses().forEach { repo.clearByStatus(it) }
                    loadCurrentTab()
                    Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Returns the statuses shown in the current tab. */
    private fun currentTabStatuses(): List<AccountStatus> = when (currentTabIdx) {
        0 -> listOf(AccountStatus.PENDING, AccountStatus.IN_PROGRESS)
        1 -> listOf(AccountStatus.BANNED)
        2 -> listOf(AccountStatus.NEW_ACCOUNT)
        3 -> listOf(AccountStatus.GOOD)
        4 -> listOf(AccountStatus.INVALID)
        else -> listOf(AccountStatus.PENDING)
    }

    private fun loadCurrentTab() {
        val accounts = repo.getByStatus(*currentTabStatuses().toTypedArray())
        adapter.items = accounts
        adapter.notifyDataSetChanged()

        binding.tvEmpty.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text = when (currentTabIdx) {
            0 -> "No pending accounts.\nAdd below (user:pass per line)."
            1 -> "No banned accounts yet."
            2 -> "No new accounts yet."
            3 -> "No good accounts yet."
            4 -> "No invalid/failed accounts."
            else -> ""
        }

        // Update tab counts
        val all = repo.getAll()
        binding.tabLayout.getTabAt(0)?.text = "Pending (${all.count { it.status == AccountStatus.PENDING || it.status == AccountStatus.IN_PROGRESS }})"
        binding.tabLayout.getTabAt(1)?.text = "Banned (${all.count { it.status == AccountStatus.BANNED }})"
        binding.tabLayout.getTabAt(2)?.text = "New (${all.count { it.status == AccountStatus.NEW_ACCOUNT }})"
        binding.tabLayout.getTabAt(3)?.text = "Good (${all.count { it.status == AccountStatus.GOOD }})"
        binding.tabLayout.getTabAt(4)?.text = "Invalid (${all.count { it.status == AccountStatus.INVALID }})"
    }

    private fun showAccountOptions(account: Account) {
        val options = mutableListOf("Delete")
        if (account.status != AccountStatus.PENDING) options.add(0, "Move to Pending")

        AlertDialog.Builder(this)
            .setTitle(account.username)
            .setMessage(if (account.note.isNotBlank()) account.note else null)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Delete" -> { repo.remove(account.id); loadCurrentTab() }
                    "Move to Pending" -> { repo.setStatus(account.id, AccountStatus.PENDING); loadCurrentTab() }
                }
            }
            .show()
    }
}

class AccountAdapter(
    var items: List<Account>,
    private val onClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val acc = items[position]
        with(holder.binding) {
            tvUsername.text = acc.username
            tvPasswordMasked.text = "●".repeat(minOf(acc.password.length, 8))
            tvNote.text = acc.note.take(50)
            tvNote.visibility = if (acc.note.isNotBlank()) View.VISIBLE else View.GONE
            val statusColor = when (acc.status) {
                AccountStatus.BANNED -> 0xFFFF5252.toInt()
                AccountStatus.NEW_ACCOUNT -> 0xFFFFD740.toInt()
                AccountStatus.GOOD -> 0xFF69F0AE.toInt()
                AccountStatus.IN_PROGRESS -> 0xFF00E5FF.toInt()
                AccountStatus.INVALID -> 0xFFFF9800.toInt()
                else -> 0xAAFFFFFF.toInt()
            }
            tvStatus.text = when (acc.status) {
                AccountStatus.IN_PROGRESS -> "RUNNING"
                AccountStatus.NEW_ACCOUNT -> "NEW"
                AccountStatus.INVALID -> "INVALID"
                else -> acc.status.name
            }
            tvStatus.setTextColor(statusColor)
        }
        holder.binding.root.setOnClickListener { onClick(acc) }
    }

    override fun getItemCount() = items.size
}
