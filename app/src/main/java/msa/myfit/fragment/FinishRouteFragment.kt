package msa.myfit.fragment

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import msa.myfit.R
import msa.myfit.domain.DatabaseVariables
import msa.myfit.domain.FinishRouteFragmentData
import msa.myfit.domain.RouteData
import msa.myfit.firebase.FirebaseUtils
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [FinishRouteFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FinishRouteFragment(mainActivity: AppCompatActivity) : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private val mainActivity = mainActivity

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
        return inflater.inflate(R.layout.fragment_finish_route, container, false)
    }

    @ExperimentalTime
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val correlationId = FirebaseAuth.getInstance().currentUser!!.uid

        val finishRouteFragmentData = FinishRouteFragmentData(
            view.findViewById(R.id.route_duration),
            view.findViewById(R.id.route_length),
            view.findViewById(R.id.route_calories),
            view.findViewById(R.id.route_score)
        )


        GlobalScope.launch {
            //data is mocked for now TODO: change
            val distanceRun = 0.5f
            val routeStartTime = OffsetDateTime.now().minusMinutes(30)

            val currentKg = retrieveWeightFromDb(correlationId)



            val routeTime = Duration.between(routeStartTime, OffsetDateTime.now())
            val caloriesBurnt = computeCaloriesBurnt(routeTime, currentKg).toFloat()

            val routeResultData = RouteData(
                routeTime = routeTime,
                distanceInKm = distanceRun,
                caloriesBurnt = caloriesBurnt,
                pointsEarned = computePointsEarned(routeTime, distanceRun, caloriesBurnt),
                startDateTime = routeStartTime.toLocalDateTime()
            )

            mainActivity.runOnUiThread {
                updateFragment(finishRouteFragmentData, routeResultData)
            }

            saveDataToDb(routeResultData, correlationId)
        }
    }

    private suspend fun retrieveWeightFromDb(correlationId: String): Float {
        val existingDocuments =  FirebaseUtils().firestoreDatabase.collection(DatabaseVariables.weightForToday)
            .whereEqualTo(DatabaseVariables.userId, correlationId)
            .get()
            .await()
            .documents

        val sortedByDateWeights = existingDocuments.map { documentSnapshot ->
            Pair(
                documentSnapshot.data!!.get(DatabaseVariables.weight).toString().toFloat(),
                LocalDate.parse(documentSnapshot.data!!.get(DatabaseVariables.inputDate).toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            )}.sortedByDescending { value -> value.second }

        if(sortedByDateWeights.isEmpty())
            return 0.0f

        return sortedByDateWeights.first().first
    }

    private fun saveDataToDb(routeResultData: RouteData, correlationId: String) {
        val routeResultDataToAdd = hashMapOf<String, Any?>(
            DatabaseVariables.userId to correlationId,
            DatabaseVariables.routeTime to routeResultData.routeTime.toString(),
            DatabaseVariables.distanceInKm to routeResultData.distanceInKm.toString(),
            DatabaseVariables.caloriesBurnt to routeResultData.caloriesBurnt.toString(),
            DatabaseVariables.pointsEarned to routeResultData.pointsEarned.toString(),
            DatabaseVariables.startDate to routeResultData.startDateTime.toString()
        )

        FirebaseUtils().firestoreDatabase.collection(DatabaseVariables.routeDatabase)
            .add(routeResultDataToAdd)
            .addOnSuccessListener {
                Log.d(TAG, "Added route with ID ${it.id}")
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error adding route $exception")
            }
    }

    @ExperimentalTime
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateFragment(finishRouteFragmentData:FinishRouteFragmentData, routeResultData: RouteData) {
        val seconds = routeResultData.routeTime.minusMinutes(routeResultData.routeTime.toMinutes()).seconds

        finishRouteFragmentData.calories.setText(routeResultData.caloriesBurnt.toString() + " kcal")
        finishRouteFragmentData.duration.setText(routeResultData.routeTime.toMinutes().toString() + ":" + seconds.toString())
        finishRouteFragmentData.length.setText(routeResultData.distanceInKm.toString() + " km")
        finishRouteFragmentData.score.setText(routeResultData.pointsEarned.toString() + " points")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun computePointsEarned(routeTime: Duration?, distanceRun: Float, caloriesBurnt: Float): Long {

        var averageSpeed: Float
        if(routeTime!!.toMinutes().equals(Duration.ofHours(0))) {
            averageSpeed = 0f
        }
        else{
            averageSpeed = distanceRun.div(routeTime.toMinutes())
        }

        return averageSpeed.times(caloriesBurnt).toLong()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun computeCaloriesBurnt(routeTime: Duration, currentKg: Float): Double {
        //Duration (in minutes)*(MET*3.5*weight in kg)/200
        return routeTime.toMinutes().times(4).times(3.5).times(currentKg).div(200)
    }
}