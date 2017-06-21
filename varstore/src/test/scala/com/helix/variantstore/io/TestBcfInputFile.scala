// © 2016 Helix OpCo LLC. All rights reserved.
// Initial Author: James Warren

package com.helix.variantstore.io

import java.io.{File, IOException}

import com.helix.TestScaffold
import com.helix.variantstore.{Call, Variant}
import org.bridj.Pointer
import org.scalatest.BeforeAndAfterAll
import resource._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.Success

//scalastyle:off magic.number
//scalastyle:off line.contains.tab
//scalastyle:off line.size.limit
class TestBcfInputFile extends TestScaffold with BeforeAndAfterAll {
  var tempFile: File = _

  override def beforeAll(): Unit = {
    tempFile = copyResourceFile("/short_test.bcf", None)
    super.beforeAll()
  }

  describe("when reading the contents of a BcfFile") {
    it("reads the correct number of variants") {
      for {bcf <- managed(new BcfInputFile(tempFile.getAbsolutePath, "testing"))} {
        bcf.iterator.length should equal(106)
      }
    }

    it("reads the correct number of contigs") {
      for {bcf <- managed(new BcfInputFile(tempFile.getAbsolutePath, "testing"))} {
        bcf.contigMap.size should equal(2842)
      }
    }

    it("correctly unmarshals variants") {
      for {bcf <- managed(new BcfInputFile(tempFile.getAbsolutePath, "testing"))} {
        //    chr1	1	    .	N	<NON_REF>	.	LOWQ	END=10176	GT:DP:GQ:MIN_DP	./.:3:0:0
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 0,
          end = 10176,
          referenceName = "chr1",
          referenceBases = "N",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(-1, -1),
            genotypeLikelihood = None,
            info = Map(
              "DP" -> Seq("3"),
              "FILTER" -> Seq("LOWQ"),
              "GQ" -> Seq("0"),
              "MIN_DP" -> Seq("0")
            )
          ))
        )))
        //    chr1	10177	.	A	AC	      .	IMP	  .	        GT:GP	./.:0.35,0.48,0.17
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 10176,
          end = 10177,
          referenceName = "chr1",
          referenceBases = "A",
          alternateBases = List("AC"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(-1, -1),
            genotypeLikelihood = None,
            info = Map(
              "GP" -> Seq("0.35", "0.48", "0.17"),
              "FILTER" -> Seq("IMP")
            )
          ))
        )))
        //    chr1	10616	.	CCGCCGTTGCAAAGGCGCGCCG	C	.	IMP	.	GT:GP	1/1:0,0,1
        bcf.iterator.next.variant should equal (Success(Variant(
          start =10615,
          end = 10637,
          referenceName = "chr1",
          referenceBases = "CCGCCGTTGCAAAGGCGCGCCG",
          alternateBases = List("C"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(1, 1),
            genotypeLikelihood = None,
            info = Map(
              "GP" -> Seq("0.0", "0.0", "1.0"),
              "FILTER" -> Seq("IMP")
            )
          ))
        )))
        // chr1	14637	.	G	<NON_REF>	.	LOWQ	END=14641	GT:DP:GQ:MIN_DP:GL	0/0:9:27:9:-0,-2.7,-30.9
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 14636,
          end = 14641,
          referenceName = "chr1",
          referenceBases = "G",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 0),
            genotypeLikelihood = Some(Seq(-0, -2.7, -30.9)),
            info = Map(
              "DP" -> Seq("9"),
              "FILTER" -> Seq("LOWQ"),
              "GQ" -> Seq("27"),
              "MIN_DP" -> Seq("9")
            )
          ))
        )))
        // chr1	14653	.	C	<NON_REF>	136.77	LOWQ	END=14653	GT:DP:GQ	./.:12:0
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 14652,
          end = 14653,
          referenceName = "chr1",
          referenceBases = "C",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(-1, -1),
            genotypeLikelihood = None,
            info = Map(
              "DP" -> Seq("12"),
              "FILTER" -> Seq("LOWQ"),
              "GQ" -> Seq("0"),
              "QUAL" -> Seq("136.77")
            )
          ))
        )))
      }
    }
  }

  override def afterAll(): Unit = {
    tempFile.delete()
    super.afterAll()
  }

}

class TestAnnotatedBcfInputFile extends TestScaffold with BeforeAndAfterAll {
  var tempFile: File = _

  override def beforeAll(): Unit = {
    tempFile = copyResourceFile("/annotated_test.bcf", None)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    tempFile.delete()
    super.afterAll()
  }

  describe("when reading the contents of an annotated BcfFile") {
    it("reads the correct number of variants") {
      for {bcf <- managed(new BcfInputFile(tempFile.getAbsolutePath, "testing"))} {
        bcf.iterator.length should equal(6)
      }
    }

    it("correctly unmarshals annotated variants") {
      for {bcf <- managed(new BcfInputFile(tempFile.getAbsolutePath, "testing"))} {
        // Test for HGVS matcher no calls
        // chr7 	117530953	.	G	<NON_REF>	.	LOWQ	END=117530953	GT:DP:GQ:MIN_DP:GL:HGVSP_NOCALL:HGVSC_NOCALL	./.:56:0:56:-0,-0,-110.3:NP_000483.3%3Ap.D110H:NM_000492.3%3Ac.328G%3EC
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 117530952,
          end = 117530953,
          referenceName = "chr7",
          referenceBases = "G",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(-1, -1),
            genotypeLikelihood = Some(Seq(-0.0, -0.0, -110.3)),
            info = Map(
              "DP" -> Seq("56"),
              "MIN_DP" -> Seq("56"),
              "GQ" -> Seq("0"),
              "HGVSC_NOCALL" -> Seq("NM_000492.3:c.328G>C"),
              "HGVSP_NOCALL" -> Seq("NP_000483.3:p.D110H"),
              "FILTER" -> Seq("LOWQ")
            )
          ))
        )))

        // Test for known allele match for HGVS coding but not protein
        // chr7 	117602868	CS900235	G	A	410.77	PASS	.	GT:AD:DP:GQ:GL:HGVSC_FOUND	0/1:31,18:49:99:-43.9,-0,-96.4:NM_000492.3%3Ac.2657%2B5G%3EA
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 117602867,
          end = 117602868,
          referenceName = "chr7",
          referenceBases = "G",
          alternateBases = List("A"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 1),
            genotypeLikelihood = Some(Seq(-43.9, -0.0, -96.4)),
            info = Map(
              "AD" -> Seq("31", "18"),
              "DP" -> Seq("49"),
              "GQ" -> Seq("99"),
              "HGVSC_FOUND" -> Seq("NM_000492.3:c.2657+5G>A"),
              "FILTER" -> Seq("PASS"),
              "QUAL" -> Seq("410.77")
            )
          ))
        )))

        // Test for variant with positive allele matches for HGVS coding and protein
        // chr7    	117606697	NONE_17	        A	T	1073.77	PASS	.	GT:AD:DP:GQ:GL:HGVSP_FOUND:HGVSC_FOUND	0/1:24,34:58:99:-110.2,-0,-76.7:NP_000483.3%3Ap.K978%2A:NM_000492.3%3Ac.2932A%3ET
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 117606696,
          end = 117606697,
          referenceName = "chr7",
          referenceBases = "A",
          alternateBases = List("T"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 1),
            genotypeLikelihood = Some(Seq(-110.2, -0.0, -76.7)),
            info = Map(
              "AD" -> Seq("24", "34"),
              "DP" -> Seq("58"),
              "GQ" -> Seq("99"),
              "HGVSC_FOUND" -> Seq("NM_000492.3:c.2932A>T"),
              "HGVSP_FOUND" -> Seq("NP_000483.3:p.K978*"),
              "FILTER" -> Seq("PASS"),
              "QUAL" -> Seq("1073.77")
            )
          ))
        )))

        // Test for very short *char values that may be null-terminated in a larger buffer
        // chr11	5226906	.	T	<NON_REF>	.	PASS	END=5226906	GT:DP:GQ:MIN_DP:GL:HGVSC_NOTFOUND:HGVSP_NOTFOUND	0/0:50:97:50:-0,-9.7,-177.6:X:XX
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 5226905,
          end = 5226906,
          referenceName = "chr11",
          referenceBases = "T",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 0),
            genotypeLikelihood = Some(Seq(-0.0, -9.7, -177.6)),
            info = Map(
              "DP" -> Seq("50"),
              "GQ" -> Seq("97"),
              "MIN_DP" -> Seq("50"),
              "FILTER" -> Seq("PASS"),
              "HGVSC_NOTFOUND" -> Seq(
                "X"
              ),
              "HGVSP_NOTFOUND" -> Seq(
                "XX"
              )
            )
          ))
        )))

        // Test for variant with no known allele match annotations
        // chr11	5226907 	.	T	<NON_REF>	.	PASS	END=5226907	GT:DP:GQ:MIN_DP:GL	0/0:50:97:50:-0,-9.7,-177.6
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 5226906,
          end = 5226907,
          referenceName = "chr11",
          referenceBases = "T",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 0),
            genotypeLikelihood = Some(Seq(-0.0, -9.7, -177.6)),
            info = Map(
              "DP" -> Seq("50"),
              "GQ" -> Seq("97"),
              "MIN_DP" -> Seq("50"),
              "FILTER" -> Seq("PASS")
            )
          ))
        )))

        // Test for variant with multiple values for negative allele matches
        // chr11	5226908	        .	G	<NON_REF>	.	PASS	END=5227415	GT:DP:GQ:MIN_DP:GL:HGVSC_NOTFOUND:HGVSP_NOTFOUND	0/0:54:99:41:-0,-9.9,-148.5:NM_000518.4%3Ac.92%2B6T%3EC,NM_000518.4%3Ac.92%2B5G%3EC,NM_000518.4%3Ac.92%2B5G%3EA,NM_000518.4%3Ac.92%2B1G%3ET,NM_000518.4%3Ac.92%2B1G%3EA,NM_000518.4%3Ac.92G%3EC,NM_000518.4%3Ac.85dupC,NM_000518.4%3Ac.82G%3ET,NM_000518.4%3Ac.79G%3EA,NM_000518.4%3Ac.75T%3EA,NM_000518.4%3Ac.68_74delAAGTTGG,NM_000518.4%3Ac.52A%3ET,NM_000518.4%3Ac.51delC,NM_000518.4%3Ac.47G%3EA,NM_000518.4%3Ac.34G%3EA,NM_000518.4%3Ac.27dupG,NM_000518.4%3Ac.25_26delAA,NM_000518.4%3Ac.20delA,NM_000518.4%3Ac.20A%3ET,NM_000518.4%3Ac.19G%3EA,NM_000518.4%3Ac.2T%3EG,NM_000518.4%3Ac.-50A%3EC,NM_000518.4%3Ac.-78A%3EG,NM_000518.4%3Ac.-79A%3EG,NM_000518.4%3Ac.-137C%3EG,NM_000518.4%3Ac.-138C%3ET,NM_000518.4%3Ac.-140C%3ET,NM_000518.4%3Ac.-151C%3ET:NP_000509.1%3Ap.R31T,NP_000509.1%3Ap.L29Pfs%2A16,NP_000509.1%3Ap.A28S,NP_000509.1%3Ap.E27K,NP_000509.1%3Ap.%3D,NP_000509.1%3Ap.E23Vfs%2A37,NP_000509.1%3Ap.K18%2A,NP_000509.1%3Ap.K18Rfs%2A2,NP_000509.1%3Ap.W16%2A,NP_000509.1%3Ap.V12I,NP_000509.1%3Ap.S10Vfs%2A14,NP_000509.1%3Ap.K9Vfs%2A14,NP_000509.1%3Ap.E7Gfs%2A13,NP_000509.1%3Ap.E7V,NP_000509.1%3Ap.E7K,NP_000509.1%3Ap.M1%3F
        bcf.iterator.next.variant should equal (Success(Variant(
          start = 5226907,
          end = 5227415,
          referenceName = "chr11",
          referenceBases = "G",
          alternateBases = List("<NON_REF>"),
          calls = List(Call(
            callSetId = "testing",
            genotype = Seq(0, 0),
            genotypeLikelihood = Some(Seq(-0.0, -9.9, -148.5)),
            info = Map(
              "DP" -> Seq("54"),
              "GQ" -> Seq("99"),
              "MIN_DP" -> Seq("41"),
              "HGVSC_NOTFOUND" -> Seq(
                "NM_000518.4:c.92+6T>C",
                "NM_000518.4:c.92+5G>C",
                "NM_000518.4:c.92+5G>A",
                "NM_000518.4:c.92+1G>T",
                "NM_000518.4:c.92+1G>A",
                "NM_000518.4:c.92G>C",
                "NM_000518.4:c.85dupC",
                "NM_000518.4:c.82G>T",
                "NM_000518.4:c.79G>A",
                "NM_000518.4:c.75T>A",
                "NM_000518.4:c.68_74delAAGTTGG",
                "NM_000518.4:c.52A>T",
                "NM_000518.4:c.51delC",
                "NM_000518.4:c.47G>A",
                "NM_000518.4:c.34G>A",
                "NM_000518.4:c.27dupG",
                "NM_000518.4:c.25_26delAA",
                "NM_000518.4:c.20delA",
                "NM_000518.4:c.20A>T",
                "NM_000518.4:c.19G>A",
                "NM_000518.4:c.2T>G",
                "NM_000518.4:c.-50A>C",
                "NM_000518.4:c.-78A>G",
                "NM_000518.4:c.-79A>G",
                "NM_000518.4:c.-137C>G",
                "NM_000518.4:c.-138C>T",
                "NM_000518.4:c.-140C>T",
                "NM_000518.4:c.-151C>T"
              ),
              "HGVSP_NOTFOUND" -> Seq(
                "NP_000509.1:p.R31T",
                "NP_000509.1:p.L29Pfs*16",
                "NP_000509.1:p.A28S",
                "NP_000509.1:p.E27K",
                "NP_000509.1:p.=",
                "NP_000509.1:p.E23Vfs*37",
                "NP_000509.1:p.K18*",
                "NP_000509.1:p.K18Rfs*2",
                "NP_000509.1:p.W16*",
                "NP_000509.1:p.V12I",
                "NP_000509.1:p.S10Vfs*14",
                "NP_000509.1:p.K9Vfs*14",
                "NP_000509.1:p.E7Gfs*13",
                "NP_000509.1:p.E7V",
                "NP_000509.1:p.E7K",
                "NP_000509.1:p.M1?"
              ),
              "FILTER" -> Seq("PASS")
            )
          ))
        )))

        bcf.iterator.hasNext should be (false)
      }
    }
  }
}

class TestBcfInputFileOpener extends TestScaffold {

  describe("when attempting to open a nonexistent file") {
    it("retries with backoff and recovers if file becomes available") {
      val filePath = s"/tmp/INITIALLY_UNAVAILABLE.bcf"
      new File(filePath).delete()

      val f: Future[Pointer[htslib.htsFile]] = Future {
        BcfInputFileOpener(3, 2).open(filePath)
      }

      Thread sleep 500
      val tempFile = copyResourceFile("/short_test.bcf", Some(filePath))
      val result = Await.result(f, 45 seconds)
      result shouldNot equal (Pointer.NULL)

      new File(filePath).delete()
    }

    it("throws an IO exception after exhausting attempts") {
      val filePath = s"/tmp/ALWAYS_UNAVAILABLE.bcf"
      new File(filePath).delete()
      val thrown: IOException = the[IOException] thrownBy BcfInputFileOpener(3, 2).open(filePath)
      thrown.getMessage should equal ("Failed to open file after 3 attempts")
    }
  }
}
//scalastyle:on magic.number
//scalastyle:on line.contains.tab
//scalastyle:on line.size.limit