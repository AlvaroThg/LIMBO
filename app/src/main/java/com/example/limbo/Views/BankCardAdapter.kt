package com.example.limbo.Views

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.limbo.R
/**
 * Adapter for displaying bank cards in a horizontal RecyclerView.
 * This provides a more efficient and flexible way to handle multiple bank cards.
 */
class BankCardAdapter(
    private val context: Context,
    private val banks: List<BankItem>
) : RecyclerView.Adapter<BankCardAdapter.BankViewHolder>() {

    class BankViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardBank: CardView = view.findViewById(R.id.cardBank)
        val cardContent: ConstraintLayout = view.findViewById(R.id.cardContent)
        val tvBankName: TextView = view.findViewById(R.id.tvBankName)
        val tvBankSubtitle: TextView = view.findViewById(R.id.tvBankSubtitle)
        val ivBankLogo: ImageView = view.findViewById(R.id.ivBankLogo)
        val btnViewLimits: Button = view.findViewById(R.id.btnViewLimits)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BankViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bank_card, parent, false)
        return BankViewHolder(view)
    }

    override fun onBindViewHolder(holder: BankViewHolder, position: Int) {
        val bank = banks[position]

        // Set bank information
        holder.tvBankName.text = bank.shortName
        holder.tvBankSubtitle.text = bank.fullName
        holder.ivBankLogo.setImageResource(bank.logoResource)

        // Set bank-specific background
        holder.cardContent.setBackgroundResource(bank.backgroundResource)

        // Set click listener for the card
        holder.cardBank.setOnClickListener {
            val intent = Intent(context, BankDetailsActivity::class.java)
            intent.putExtra("BANK_ID", bank.id)
            context.startActivity(intent)
        }

        // Button click listener can be set separately if needed
        holder.btnViewLimits.setOnClickListener {
            val intent = Intent(context, BankDetailsActivity::class.java)
            intent.putExtra("BANK_ID", bank.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = banks.size
}

/**
 * Data class representing a bank item in the horizontal list.
 */
data class BankItem(
    val id: String,
    val shortName: String,
    val fullName: String,
    val logoResource: Int,
    val backgroundResource: Int,
    val features: List<String> = emptyList() // Opcional
)