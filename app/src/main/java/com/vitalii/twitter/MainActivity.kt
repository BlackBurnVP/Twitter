package com.vitalii.twitter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.add_tweet.*
import kotlinx.android.synthetic.main.add_tweet.view.*
import kotlinx.android.synthetic.main.item_tweet.view.*
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.collections.HashMap
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.android.synthetic.main.ads_tweet.view.*


class MainActivity : AppCompatActivity() {

    private val tweetsList = ArrayList<Tweet>()
    private val adapter = TweetsAdapter(this,tweetsList)
    private lateinit var myEmail:String
    private lateinit var myUID:String
    private var database= FirebaseDatabase.getInstance()
    private var mStorageRef: StorageReference? = null
    private var myRef=database.reference
    private lateinit var mAdView: AdView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl("gs://twitter-c0cc9.appspot.com")

        MobileAds.initialize(this,"ca-app-pub-7140796069074516~4040324116")

        val bundle = intent.extras
        myEmail = bundle!!.getString("email")!!
        myUID = bundle.getString("uid")
        addTweets()
        loadTweets()

        lvTweets.adapter = adapter
    }

    private fun addTweets(){
        tweetsList.add(Tweet("Some tweet", "url", "add"))
    }

    private val PICK_IMAGE_CODE = 123
    fun loadImage(){
        val intent = Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent,PICK_IMAGE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode==PICK_IMAGE_CODE && resultCode== Activity.RESULT_OK && data!=null){
            val selectedImage = data.data
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(selectedImage!!,filePathColumn, null,null,null)
            cursor.moveToFirst()
            val columnIndex = cursor.getColumnIndex(filePathColumn[0])
            val picPath = cursor.getString(columnIndex)
            cursor.close()
            uploadImage(BitmapFactory.decodeFile(picPath))
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private var downloadURL:String? = null
    private fun uploadImage(bitmap: Bitmap){
        tweetsList.add(0, Tweet("","","loading"))
        adapter.notifyDataSetChanged()
        val df = SimpleDateFormat("ddMMyyHHmmss")
        val imagePath = df.format(Date())+".jpg"
        val imageRef = mStorageRef!!.child("imagePost/$imagePath")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)
        uploadTask.addOnFailureListener{

        }.addOnSuccessListener {taskSnapshot ->
            downloadURL = imageRef.downloadUrl.toString()
            tweetsList.removeAt(0)
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadTweets(){
        myRef.child("Posts")
            .addValueEventListener(object :ValueEventListener{
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    try {
                        tweetsList.clear()
                        tweetsList.add(Tweet("Some tweet", "url", "add"))
                        tweetsList.add(Tweet("Some tweet", "url", "ads"))
                        val td = dataSnapshot.value as HashMap<String,Any>
                        for (key in td.keys){
                            var post = td[key] as HashMap<String,Any>
                            tweetsList.add(Tweet(post["tweetText"].toString(),
                                post["tweetImageURL"].toString(),
                                post["tweetPersonUID"].toString()))
                        }
                    adapter.notifyDataSetChanged()
                    }catch (ex:Exception){}
                }
                override fun onCancelled(p0: DatabaseError) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }
            })
    }

    inner class TweetsAdapter(context: Context, var tweetsList: ArrayList<Tweet>):BaseAdapter() {

        private var context: Context? = context

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val myTweet = tweetsList[position]

            when(myTweet.tweetPersonUID){

                "add" -> {
                    val myView = layoutInflater.inflate(R.layout.add_tweet, null)
                    myView.ivAttach.setOnClickListener {
                        loadImage()
                    }
                    myView.ivPost.setOnClickListener {
                        val tweetText = edTweet.text.toString()
                        if (downloadURL == null) {
                            downloadURL = "No pic"
                        }
                        myRef.child("Posts").push().setValue(Tweet(tweetText, downloadURL, myUID!!))
                        myView.edTweet.setText("")
                    }
                    return myView
                }

                "loading" -> {
                    return layoutInflater.inflate(R.layout.loading_tweet, null)
                }

                "ads" -> {
                    val myView = layoutInflater.inflate(R.layout.ads_tweet, null)
                    mAdView = myView.adView
                    //TODO: Remove addTestDevice statement from production version
                    val adRequest = AdRequest.Builder().addTestDevice("9916DEA341BF7D01F7D1B0D09D334F78").build()
                    mAdView.loadAd(adRequest)
                    return myView
                }

                else -> {
                    val myView = layoutInflater.inflate(R.layout.item_tweet,null)
                    myView.txtTweetBody.text = myTweet.tweetText
                    if (myTweet.tweetImageURL!="No pic" && myTweet.tweetImageURL!!.isNotEmpty()){
                        //TODO: Use Picasa for load from URL
                        val imageURL = URL(myTweet.tweetImageURL)
                        val bmp = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream())
                        myView.ivTweetPic.setImageBitmap(bmp)
                    }

                    myRef.child("Users").child(myTweet.tweetPersonUID)
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                try {
                                    val td = dataSnapshot.value as HashMap<String,Any>
                                    for (key in td.keys){
                                        val userInfo = td[key] as String
                                        if (key == "ProfileImage"){
                                            val imageURL = URL(userInfo)
                                            val bmp = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream())
                                            myView.ivPersonIcon.setImageBitmap(bmp)
                                        }else{
                                            myView.txtTweetUser.text = userInfo
                                        }
                                    }
                                }catch (ex: Exception){}
                            }
                            override fun onCancelled(p0: DatabaseError) {
                                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                            }
                        })
                    return myView
                }
            }
        }

        override fun getItem(position: Int): Any {
            return tweetsList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return tweetsList.size
        }
    }
}

