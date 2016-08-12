package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.Configuration

import com.dropbox.core.{ DbxAppInfo, DbxAuthFinish, DbxEntry, DbxWebAuthNoRedirect }
import com.dropbox.core.DbxEntry.WithChildren

import scalaj.http.Http

import dropbox4s.core.CoreApi
import dropbox4s.core.model.DropboxPath

import scala.language.postfixOps
import scala.collection.JavaConverters._

object Dropbox extends Controller with CoreApi {

  val app: Application = Play.unsafeApplication
  val conf: Configuration = app.configuration

  val applicationName = conf.getString("dropbox.app.name").get
  val version = conf.getString("dropbox.app.version").get
  val redirectUri = conf.getString("dropbox.app.redirectUri").get
  val appKey = conf.getString("dropbox.app.key").get
  val appSecret = conf.getString("dropbox.app.secret").get
  val appInfo = new DbxAppInfo(appKey, appSecret);
  val webAuth = new DbxWebAuthNoRedirect(requestConfig, appInfo);

  def index = Action { request =>
    val csrf = models.Dropbox.generateCsrf
    Ok(views.html.dropbox.index(appKey, redirectUri, csrf)).withSession(request.session + ("csrf" -> csrf))
  }

  def authFinish(code: String, state: String) = Action { request =>
    request.session.get("csrf").map { csrf =>
      if (csrf == state) {
        val response = Http("https://api.dropbox.com/1/oauth2/token")
          .postForm(Seq("code" -> code, "grant_type" -> "authorization_code", "redirect_uri" -> redirectUri))
          .auth(appKey, appSecret)
          .asString
        if (response.code == 200) {
          val json: JsValue = Json.parse(response.body)
          val access_token: String = (json \ "access_token").as[String]
          Ok(views.html.dropbox.selectForm(access_token)).withSession(request.session + ("access_token" -> access_token))
        } else {
          InternalServerError("Error when finishing the oAuth process: " + response.body)
        }
      } else
        Unauthorized("Csrf values doesn't match.")
    }.getOrElse {
      Unauthorized("Bad csrf value.")
    }
  }

  def listDirectory(accessToken: String) = Action { request =>
    implicit val auth: DbxAuthFinish = new DbxAuthFinish(accessToken, "", "")
    val appPath = DropboxPath("/")
    val remoteFile = DropboxPath("/9AugFolderForReadAndWrite/karra12.txt")
    val uploadedFile = remoteFile downloadTo "/home/himanshu/Downloads/testfile/ csv/dropboxdownload.csv"
    val children: List[DbxEntry] = (appPath children).children.asScala.toList
    Ok(views.html.dropbox.listDirectory(children))
  }

  def uploadToFolder(accesToken: String) = Action { request =>
    implicit val auth: DbxAuthFinish = new DbxAuthFinish(accesToken, "", "")
    val appPath = DropboxPath("/")
    val localFile = new java.io.File("/home/himanshu/Downloads/testfile/ csv/mariadata_200.csv")
    val remoteFile = DropboxPath("/9AugFolderForReadAndWrite/karra12.txt")
    val uploadedFile = localFile uploadTo remoteFile
    Ok(views.html.dropbox.uploadFile(uploadedFile.name))
  }

  def downloadFromFolder(accessToken: String) = Action { request =>
    implicit val auth: DbxAuthFinish = new DbxAuthFinish(accessToken, "", "")
    val appPath = DropboxPath("/")
    val remoteFile = DropboxPath("/9AugFolderForReadAndWrite/karra12.txt")
    val uploadedFile = remoteFile downloadTo "/home/himanshu/Downloads/testfile/ csv/file10aug.csv"
    val fileName = uploadedFile.name
    Ok(views.html.dropbox.downloadFile(fileName))
  }

  def downloadAllFileFromFolder(accessToken: String) = Action { request =>
    implicit val auth: DbxAuthFinish = new DbxAuthFinish(accessToken, "", "")
    val appPath2 = DropboxPath("/9AugFolderForReadAndWrite")
    val remoteFile = DropboxPath("/9AugFolderForReadAndWrite/karra12.txt")
    val children: List[DbxEntry] = (appPath2 children).children.asScala.toList
    val fileNameList = children map { x =>
      getDropBoxPath(x.name) downloadTo s"/home/himanshu/Downloads/testfile/drop2/${x.name}"
      x.name
    }
    val fileName = fileNameList.toList
    Ok(views.html.dropbox.downloadFiles(fileName))
  }
  
  def uploadFilesToFolder(accesToken: String) = Action { request =>
    implicit val auth: DbxAuthFinish = new DbxAuthFinish(accesToken, "", "")
    val appPath = DropboxPath("/")
     val allFileFromFolder = new java.io.File("/home/himanshu/Downloads/testfile/ csv/")
    val files = allFileFromFolder.listFiles().map{x=>
      new java.io.File(s"/home/himanshu/Downloads/testfile/ csv/${x.getName}") uploadTo DropboxPath(s"/akshay/${x.getName}")
      x.getName
    }
    Ok(views.html.dropbox.uploadFiles(files.toList))
  }

  def getDropBoxPath(fileName: String): DropboxPath = {
    val remoteFile = DropboxPath(s"/9AugFolderForReadAndWrite/${fileName}")
    remoteFile
  }
}

