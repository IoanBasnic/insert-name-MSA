package msa.myfit.fragment

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import msa.myfit.R
import msa.myfit.databinding.ActivityMainBinding
import msa.myfit.domain.UserProfileFragmentData
import msa.myfit.firebase.FirebaseUtils


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MyProfileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

const val TAG = "FIRESTORE"

class MyProfileFragment(mainActivity: AppCompatActivity) : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var binding: ActivityMainBinding? = null

    private val mainActivity = mainActivity
    private var retrievedDocuments: MutableList<DocumentSnapshot>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_profile, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MyProfileFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String, mainActivity: AppCompatActivity) =
            MyProfileFragment(mainActivity).apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fragmentActivity: Activity? = activity

        val correlationId = FirebaseAuth.getInstance().currentUser!!.uid

        val userProfileFragment = UserProfileFragmentData(
            btnUpdateProfile = view.findViewById(R.id.btn_update_profile),
            firstName = view.findViewById(R.id.edit_first_name),
            lastName = view.findViewById(R.id.edit_last_name),
            gender = view.findViewById(R.id.spinner_gender),
            age = view.findViewById(R.id.edit_age),
            height = view.findViewById(R.id.edit_height)
        )

        var existingDocument: DocumentSnapshot? = null
        GlobalScope.launch {
            existingDocument = getUserProfilesFromDbAndUpdateView(correlationId, userProfileFragment)
        }

        if (fragmentActivity != null) {
            ArrayAdapter.createFromResource(
                fragmentActivity,
                R.array.gender_options_array,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                // Apply the adapter to the spinner
                userProfileFragment.gender.adapter = adapter
            }
        }


        userProfileFragment.btnUpdateProfile.setOnClickListener {

            if(!retrievedDocuments.isNullOrEmpty())
                existingDocument = retrievedDocuments!![0]

            val userProfileToAdd = hashMapOf<String, Any?>(
                "correlation_id" to correlationId,
                "first_name" to userProfileFragment.firstName.text.toString(),
                "last_name" to userProfileFragment.lastName.text.toString(),
                "gender" to userProfileFragment.gender.selectedItem.toString(),
                "age" to userProfileFragment.age.text.toString(),
                "height" to userProfileFragment.height.text.toString()
            )

            if(existingDocument != null){
                FirebaseUtils().firestoreDatabase.collection("user_profiles")
                    .document(existingDocument!!.id)
                    .set(userProfileToAdd, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Updated user profile with correlation id $correlationId")

                        mainActivity.runOnUiThread {
                            updateUserProfileData(
                                userProfileToAdd,
                                userProfileFragment
                            )
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "Error adding user profile $exception")
                    }
            }
            else{
                FirebaseUtils().firestoreDatabase.collection("user_profiles")
                    .add(userProfileToAdd)
                    .addOnSuccessListener {
                        Log.d(TAG, "Added user profile with ID ${it.id}")

                        mainActivity.runOnUiThread {
                            updateUserProfileData(
                                userProfileToAdd,
                                userProfileFragment
                            )
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "Error adding user profile $exception")
                    }
            }

            existingDocument = null
            retrievedDocuments = null
            GlobalScope.launch {
                retrievedDocuments = getUserProfilesFromDb(correlationId)
            }
        }
    }

    private suspend fun getUserProfilesFromDbAndUpdateView(correlationId: String, userProfileFragment: UserProfileFragmentData): DocumentSnapshot? {
        mainActivity.runOnUiThread{enableUserProfileFragment(userProfileFragment, false)}

        val userProfiles = getUserProfilesFromDb(correlationId)

        if(userProfiles.size > 1){
            Log.w(TAG, "Error user has more than one user profiles")
        }

        if(userProfiles.isNotEmpty()) {
            val existingUserProfile = userProfiles[0]

            mainActivity.runOnUiThread {
                setExistingUserProfileDataToFragment(
                    existingUserProfile,
                    userProfileFragment
                )
            }
        }

        mainActivity.runOnUiThread{enableUserProfileFragment(userProfileFragment, true)}

        if(userProfiles.isEmpty())
            return null
        return userProfiles[0]
    }

    private fun setExistingUserProfileDataToFragment(
        userProfile: DocumentSnapshot,
        userProfileFragment: UserProfileFragmentData
    ){
        val fn = userProfile.data!!["first_name"]
        if (fn != null) {
            userProfileFragment.firstName.setText(fn.toString())
        }

        val ln = userProfile.data!!["last_name"]
        if (ln != null) {
            userProfileFragment.lastName.setText(ln.toString())
        }

        val g = userProfile.data!!["gender"]
        if (g != null) {
            val spinnerPosition: Int = getIndex(userProfileFragment.gender, g.toString())
            userProfileFragment.gender.setSelection(spinnerPosition)
        }

        val a = userProfile.data!!["age"]
        if (a != null) {
            userProfileFragment.age.setText(a.toString())
        }

        val h = userProfile.data!!["height"]
        if (h != null) {
            userProfileFragment.height.setText(h.toString())
        }
    }

    private fun updateUserProfileData(userProfileToAdd: HashMap<String, Any?>, userProfileFragment: UserProfileFragmentData){
        val fn = userProfileToAdd["first_name"]
        if (fn != null) {
            userProfileFragment.firstName.setText(fn.toString())
        }

        val ln = userProfileToAdd["last_name"]
        if (ln != null) {
            userProfileFragment.lastName.setText(ln.toString())
        }

        val g = userProfileToAdd["gender"]
        if (g != null) {
            val spinnerPosition: Int = getIndex(userProfileFragment.gender, g.toString())

            userProfileFragment.gender.setSelection(spinnerPosition)
        }

        val a = userProfileToAdd["age"]
        if (a != null) {
            userProfileFragment.age.setText(a.toString())
        }

        val h = userProfileToAdd["height"]
        if (h != null) {
            userProfileFragment.height.setText(h.toString())
        }
    }


    private fun getIndex(spinner: Spinner, myString: String): Int {
        for (i in 0 until spinner.count) {
            if (spinner.getItemAtPosition(i).toString().equals(myString, ignoreCase = true)) {
                return i
            }
        }
        return 0
    }

    private fun enableUserProfileFragment(userProfileFragment: UserProfileFragmentData, shouldBe: Boolean){
        userProfileFragment.firstName.isEnabled = shouldBe;
        userProfileFragment.lastName.isEnabled = shouldBe;
        userProfileFragment.gender.isEnabled = shouldBe;
        userProfileFragment.age.isEnabled = shouldBe;
        userProfileFragment.height.isEnabled = shouldBe;
    }

    private suspend fun getUserProfilesFromDb(correlationId: String): MutableList<DocumentSnapshot>{
        return FirebaseUtils().firestoreDatabase.collection("user_profiles")
            .whereEqualTo("correlation_id", correlationId)
            .get()
            .await()
            .documents
    }
}
