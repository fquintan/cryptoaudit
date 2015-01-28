package main.scala.lift.rest

import java.io.InputStream
import java.math.BigInteger

import main.scala.SecureBroadcastChannel.BlockchainPublisher
import main.scala.commitment.Commitment
import main.scala.filecommitment.StringArrayCommitment
import main.scala.persistance.committedLine.{CommittedLineDAO, CommittedLine}
import main.scala.persistance.file.{FileDAO, File}
import main.scala.persistance.transaction.{TransactionDAO, Transaction}
import main.scala.persistance.committedLine.CommittedLineDAO
import net.liftweb.common.{Empty, Box, Full}
import net.liftweb.http.{FileParamHolder, S, Req}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._

import scala.io.Source

/**
 * Created by fquintanilla on 26-01-15.
 */
object FileUploadRest extends RestHelper{

  case class FileUploadResponse(val file_id : String)
  /**
   * Serve the specified URL
   *
   * e.g. case "hello" :: "world" :: Nil Get _ => ...
   * will respond to a GET request on the http://<host>/hello/world/ URL
   * */
  serve{
    // /api/file/
    case "api" :: "file" :: Nil Post req =>
      for {
        content  <- getUploadedFile(req) ?~ "You forgot to upload a file"
        response <- processFile(content) ?~ "The file couldn't be processed"
      }yield Extraction.decompose(response)
  }

  private def getUploadedFile(req : Req) : Box[FileParamHolder] = {
    req.uploadedFiles(0)
    val files = req.uploadedFiles
    if (files.length == 0) return Empty
    Full(files(0))
  }

  private def processFile(fph : FileParamHolder) : Box[FileUploadResponse] = {
    val fileStream : InputStream = fph.fileStream
    val filename : String = fph.fileName
    val content : String = Source.fromInputStream(fileStream).mkString
    val lines = content.split("\n")
    val comm : StringArrayCommitment = new StringArrayCommitment(lines)
    // TODO: Calcular BitcoinTransaction
    /* replace '1' for the private key from configuration
    val pKey : BigInteger = new BigInteger("1")
    val publisher = new BlockchainPublisher(pKey, comm.commitment)
    val tx_hash = publisher.publish()
    */
    val tx_hash = comm.commitment
    val commitment = new Commitment(comm.root, comm.random)
    val tx = new Transaction(tx_hash, commitment)
    val txId = TransactionDAO.insert(tx)
    if (txId == None) return Empty
    val proofs = comm.proofArray
    for ((line, proof) <- proofs){
      val commLine = new CommittedLine(line,proof,txId.get)
      CommittedLineDAO.insert(commLine)
    }

    val file : File = new File(txId, filename)
    val fileId = FileDAO.insert(file)
    if (fileId == None) return Empty
    Full(FileUploadResponse(fileId.get.toString))
  }

}
