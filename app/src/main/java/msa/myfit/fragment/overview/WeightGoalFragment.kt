package msa.myfit.fragment.overview

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import msa.myfit.R
import msa.myfit.domain.DatabaseVariables
import msa.myfit.firebase.FirebaseUtils
import msa.myfit.fragment.HomeFragment
import msa.myfit.fragment.my_profile.TAG

class WeightGoalFragment(
    private val mainActivity: AppCompatActivity,
    private var existingDocuments: MutableList<DocumentSnapshot>?
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        if(existingDocuments.isNullOrEmpty()){
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.fragment_weight_goal, container, false)
        }

        return inflater.inflate(R.layout.fragment_weight_goal_already_inputted, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val correlationId = FirebaseAuth.getInstance().currentUser!!.uid

        if(!existingDocuments.isNullOrEmpty()){
            val textView: TextView = view.findViewById(R.id.weight_goal_already_inputted)

            val existingDocument = existingDocuments!![0]
            if(existingDocument != null){
                textView.setText(existingDocument.data!![DatabaseVariables.weight].toString() + " Kg")
            }

            val editWeightGoalBtn: Button = view.findViewById(R.id.btn_set_weight_goal)
            val weightToday: TextInputEditText = view.findViewById(R.id.edit_weight_goal_for_today)

            editWeightGoalBtn.setOnClickListener {
                val weightToSave = weightToday.text.toString().trim { it <= ' '}

                when{
                    TextUtils.isEmpty(weightToSave) -> {
                        Toast.makeText(
                            mainActivity,
                            "Please enter your weight goal for today",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    !TextUtils.isDigitsOnly(weightToSave) -> {
                        Toast.makeText(
                            mainActivity,
                            "Please enter only digits for your weight",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        val weightGoalToAdd = hashMapOf<String, Any?>(
                            DatabaseVariables.userId to correlationId,
                            DatabaseVariables.weight to weightToSave
                        )

                        FirebaseUtils().firestoreDatabase.collection(DatabaseVariables.weightGoalDatabase)
                            .document(existingDocument.id)
                            .set(weightGoalToAdd, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "Updated weight goal for user with ID ${correlationId}")

                                var fragment: Fragment? =
                                    HomeFragment(mainActivity)
                                replaceFragment(fragment)
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Error adding weight goal $exception")
                            }
                    }
                }
            }
        }
        else{
            val setWeightTodayBtn: Button = view.findViewById(R.id.btn_set_weight_goal)
            val weightToday: TextInputEditText = view.findViewById(R.id.edit_weight_goal_for_today)

            setWeightTodayBtn.setOnClickListener {
                val weightToSave = weightToday.text.toString().trim { it <= ' '}

                when{
                    TextUtils.isEmpty(weightToSave) -> {
                        Toast.makeText(
                            mainActivity,
                            "Please enter your weight goal for today",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    !TextUtils.isDigitsOnly(weightToSave) -> {
                        Toast.makeText(
                            mainActivity,
                            "Please enter only digits for your weight",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        val weightGoalToAdd = hashMapOf<String, Any?>(
                            DatabaseVariables.userId to correlationId,
                            DatabaseVariables.weight to weightToSave
                        )

                        FirebaseUtils().firestoreDatabase.collection(DatabaseVariables.weightGoalDatabase)
                            .add(weightGoalToAdd)
                            .addOnSuccessListener {
                                Log.d(TAG, "Added weight goal with ID ${it.id}")

                                var fragment: Fragment? =
                                    HomeFragment(mainActivity)
                                replaceFragment(fragment)
                            }
                            .addOnFailureListener { exception ->
                                Log.w(TAG, "Error adding weight goal $exception")
                            }
                    }
                }
            }
        }
    }

    private fun replaceFragment(someFragment: Fragment?) {
        val transaction: FragmentTransaction = requireFragmentManager().beginTransaction()
        if (someFragment != null) {
            transaction.replace(R.id.HomeFragmentId, someFragment)
        }
        transaction.addToBackStack(null)
        transaction.commit()
    }
}