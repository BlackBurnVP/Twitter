package com.vitalii.twitter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.view.View
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_login.*
import com.google.firebase.storage.StorageReference
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.FirebaseDatabase




class LoginActivity : AppCompatActivity() {

    private var mAuth:FirebaseAuth? = null
    private var mFirebaseAnalytics:FirebaseAnalytics? = null
    private var mStorageRef: StorageReference? = null
    private var database = FirebaseDatabase.getInstance()
    private var myRef = database.getReference("message")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        mStorageRef = FirebaseStorage.getInstance().getReferenceFromUrl("gs://twitter-c0cc9.appspot.com")

        ivPersonImage.setOnClickListener{
            checkPermission()
        }

        btnLogin.setOnClickListener(login)
    }

    private val login = View.OnClickListener {
        loginToFirebase(edEmail.text.toString(),edPassword.text.toString())
    }

    private fun loginToFirebase(email:String,password:String){
        mAuth!!.createUserWithEmailAndPassword(email,password)
            .addOnCompleteListener(this){task ->
                if(task.isSuccessful){
                    Toast.makeText(this,"Successful login",Toast.LENGTH_SHORT).show()
                    saveImageInFirebase()
                }else{
                    Toast.makeText(this,"Failed to login",Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveImageInFirebase(){
        val currentUser = mAuth!!.currentUser
        if (ivPersonImage.drawable.constantState != resources.getDrawable(R.drawable.persoicon).constantState){
            val df = SimpleDateFormat("ddMMyyHHmmss")
            val imagePath = df.format(Date())+".jpg"
            val imageRef = mStorageRef!!.child("images/$imagePath")
            ivPersonImage.isDrawingCacheEnabled = true
            ivPersonImage.buildDrawingCache()
            val drawable = ivPersonImage.drawable as BitmapDrawable
            val bitmap = drawable.bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,baos)
            val data = baos.toByteArray()
            val uploadTask = imageRef.putBytes(data)
            uploadTask.addOnFailureListener{

            }.addOnSuccessListener {taskSnapshot ->
                val downloadUrl = imageRef.downloadUrl.toString()

                myRef.child("Users").child(currentUser!!.uid).child("email").setValue(currentUser.email)
                myRef.child("Users").child(currentUser!!.uid).child("ProfileImage").setValue(downloadUrl)
            }
        }else{
            myRef.child("Users").child(currentUser!!.uid).child("email").setValue(currentUser.email)
            myRef.child("Users").child(currentUser!!.uid).child("ProfileImage").setValue("gs://twitter-c0cc9.appspot.com/images/persoicon.png")
        }
        toMain()
    }

    override fun onStart() {
        super.onStart()
        toMain()
    }

    private fun toMain(){
        val currentUser = mAuth!!.currentUser
        if(currentUser!=null){
            val intent = Intent(this,MainActivity::class.java)
            intent.putExtra("email",currentUser.email)
            intent.putExtra("uid",currentUser.uid)
            startActivity(intent)
        }
    }

    private val READIMAGE = 123
    private fun checkPermission(){
        if (Build.VERSION.SDK_INT>=23){
            if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),READIMAGE)
            }
        }
        loadImage()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            READIMAGE->{
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    loadImage()
                }else{
                    Toast.makeText(this,"Cannot access images",Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private val PICK_IMAGE_CODE = 123
    private fun loadImage(){
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
            ivPersonImage.setImageBitmap(BitmapFactory.decodeFile(picPath))
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
