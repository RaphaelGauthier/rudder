/*
*************************************************************************************
* Copyright 2022 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.rudder.domain.reports

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.mutable.ArrayBuffer


/**
 * Test properties about Compliance Level,
 * especially pourcent related (sum to 100, rounding error, etc)
 */


@RunWith(classOf[JUnitRunner])
class TestComplianceLevel extends Specification {

  sequential

  "we can sort levels" should {

    "sort smallest first" in {

      val c = ComplianceLevel(12, 1, 3, 4, 9, 11, 0, 8, 5, 2, 6, 13, 10, 7)

      CompliancePercent.sortLevels(c).map(_._1) === List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

    }

    "sort equals in order of worse last" in {
      val c = ComplianceLevel(4, 4, 4, 4, 9, 11, 1, 4, 5, 2, 6, 13, 10, 7)
      // interresting part is the 5 '4', where:
      // (4, 7 = not applicable) < (4, 1 = success) < (4, 0 = pending) < (4, 2 = repaired) < (4, 3 = error)
      CompliancePercent.sortLevels(c) ===
        List((1, 6), (2, 9), (4, 7), (4, 1), (4, 0), (4, 2), (4, 3), (5, 8), (6, 10), (7, 13), (9, 4), (10, 12), (11, 5), (13, 11))
    }

    "sort error after missing " in {
      val c = ComplianceLevel(0, 2, 0, 2, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0)
      CompliancePercent.sortLevels(c) ===
        List((0,7), (0,10), (0,9), (0,0), (0,8), (0,6), (0,2), (0,11), (0,12), (0,4), (0,13), (2,1), (2,5), (2,3))
    }
  }


  "a compliance must never be rounded below its precision" >> {

    "which is 0.01 by default" >> {
      val c = ComplianceLevel(success = 1, error = 100000)
      val pc = c.computePercent()
      (pc.repaired === 0) and (pc.success === 0.01) and (pc.error === 99.99)
    }

    "and can be 0" >> {
      val c = ComplianceLevel(success = 1, error = 100000)
      val pc = CompliancePercent.fromLevels(c, 0)
      (pc.repaired === 0) and (pc.success === 1) and (pc.error === 99)
    }

    "or a lot" >> {
      val c = ComplianceLevel(success = 1, error = 1000000000)
      val pc = CompliancePercent.fromLevels(c, 5)
      (pc.repaired === 0) and (pc.success === 0.00001) and (pc.error === 99.99999)
    }

  }

  "new compliance system must never be rounded below its precision" >> {

    "which is 0.01 by default" >> {
      val c = ComplianceLevel(success = 1, error = 100000)
      val pc = c.computeCorrectPercent()
      (pc.repaired === 0) and (pc.success === 0.01) and (pc.error === 99.99)
    }

    "and can be 0" >> {
      val c = ComplianceLevel(success = 1, error = 100000)
      val pc = CompliancePercent.correctFromLevels(c, 0)
      (pc.repaired === 0) and (pc.success === 1) and (pc.error === 99)
    }

    "or a lot" >> {
      val c = ComplianceLevel(success = 1, error = 1000000000)
      val pc = CompliancePercent.correctFromLevels(c, 5)
      (pc.repaired === 0) and (pc.success === 0.00001) and (pc.error === 99.99999)
    }

  }
  "compliance when there is no report is 0" >> {
    ComplianceLevel().computeCorrectPercent().compliance === 0
  }

  "Compliance with old method must sum to 100 percent" >> {

    "when using default precision" >> {
      val c = ComplianceLevel(0, 1, 1, 1)
      val pc = c.computePercent()
      (pc.success === 33.33) and (pc.repaired === 33.33) and (pc.error === 33.34)
    }

    "when using default precision and having ignore pending" >> {
      val c = ComplianceLevel(12, 1, 1, 1)
      val pc = c.withoutPending.computePercent()
      (pc.success === 33.33) and (pc.repaired === 33.33) and (pc.error === 33.34)
    }

    "when using 0 digits" >> {
      val c = ComplianceLevel(0, 1, 1, 1)
      val pc = c.computePercent(0)
      (pc.success === 33) and (pc.repaired === 33) and (pc.error === 34)
    }

    "when using 0 digits and ignoring pending" >> {
      val c = ComplianceLevel(5, 1, 1, 1)
      val pc = c.withoutPending.computePercent(0)
      (pc.success === 33) and (pc.repaired === 33) and (pc.error === 34)
    }

    "when using 0 digits and ignoring pending with small error" >> {
      val c = ComplianceLevel(5, 1000, 1000, 1)
      val pc = c.withoutPending.computePercent(0)
      (pc.success === 50) and (pc.repaired === 49) and (pc.error === 1)
    }

    " when using 0 digits, should round keep ordering" >> {
      val c = ComplianceLevel(0, 200, 199, 1, 1, 1, 1)
      val pc = c.withoutPending.computePercent(0)
      (pc.success === 50) and (pc.repaired === 49) and (pc.error === 1) and (pc.unexpected === 1) and (pc.missing === 1)
    }
  }

  "Compliance with correct method must sum to 100 percent" >> {

    "when using default precision" >> {
      val c = ComplianceLevel(0, 1, 1, 1)
      println("****************************************")
      val pc = c.computeCorrectPercent()
      val compliance = c.complianceWithoutPending()

      (pc.success === 33.33) and (pc.repaired === 33.33) and (pc.error === 33.34) and (compliance === 66.66)
    }

    "when using default precision and having ignore pending" >> {
      val c = ComplianceLevel(12, 1, 1, 1)
      val pc = c.withoutPending.computeCorrectPercent()
      val compliance = c.complianceWithoutPending()
      (pc.success === 33.33) and (pc.repaired === 33.33) and (pc.error === 33.34) and (compliance === 66.66)
    }

    "when using 0 digits" >> {
      val c = ComplianceLevel(0, 1, 1, 1)
      val pc = c.computeCorrectPercent(0)
      val compliance = c.complianceWithoutPending()
      (pc.success === 33) and (pc.repaired === 33) and (pc.error === 34) and (compliance === 66)
    }

    "when using 0 digits and ignoring pending" >> {
      val c = ComplianceLevel(5, 1, 1, 1)
      val pc = c.withoutPending.computeCorrectPercent(0)
      val compliance = c.complianceWithoutPending()
      (pc.success === 33) and (pc.repaired === 33) and (pc.error === 34) and (compliance === 66)
    }

    "when using 0 digits and ignoring pending with small error" >> {
      val c = ComplianceLevel(5, 1000, 1000, 1)
      val pc = c.withoutPending.computeCorrectPercent(0)
      val compliance = c.complianceWithoutPending()
      (pc.success === 49) and (pc.repaired === 50) and (pc.error === 1) and (compliance === 99)
    }

    " when using 0 digits, should round keep ordering" >> {
      val c = ComplianceLevel(0, 200, 199, 1, 1, 1, 1)
      val pc = c.withoutPending.computeCorrectPercent(0)
      val compliance = c.complianceWithoutPending()
      println(pc)
      (pc.success === 49) and (pc.repaired === 47) and (pc.error === 1) and (pc.unexpected === 1) and (pc.missing === 1) and (compliance === 96)
    }
  }

  "Compliance computation must" >> {
    "return 66.66 with default precision" >> {
      val c = ComplianceLevel(0, 1, 1, 1)
      val pc = c.computePercent().compliance
      pc === 66.66
    }
    "when not round up the error with default precision" >> {
      val c = ComplianceLevel(0, 1, 1, 4)
      val pc = c.computePercent().compliance
      pc === 33.34
    }
    "when not round up the error with default precision and ignoring pending" >> {
      val c = ComplianceLevel(1, 2, 2, 8)
      val pc = c.withoutPending.computePercent().compliance
      pc === 33.34
    }
    "return correctly rounded values" >> {
      val c = ComplianceLevel(0, 1000, 1000, 1)
      val pc = c.computePercent().compliance
      pc === 99.95
    }

    "when not round up the error with default precision and ignoring pending using direct computation" >> {
      val c = ComplianceLevel(1, 2, 2, 8)
      val pc = c.withoutPending.computePercent().compliance
      pc === 33.34
    }
    "return correctly with direct computation and rounded values" >> {
      val c = ComplianceLevel(0, 1000, 1000, 1)
      val pc = c.complianceWithoutPending()
      pc === 99.95
    }
  }

  "Compliance without pending computation must" >> {
    val complianceLevel:ArrayBuffer[ComplianceLevel] = new ArrayBuffer[ComplianceLevel](100000)
    "return the same" >> {
      println("starting list")
      for (s <- 0 to 9) {
        for (r <- 0 to 9) {
          for (e <- 0 to 9) {
            for (m <- 0 to 9) {
              for (nc <- 0 to 9) {
                complianceLevel.addOne(ComplianceLevel(success = s, repaired = r, error = e, missing = m, nonCompliant = nc))
              }
            }
          }
        }
      }
      println("arraybuffer completed")


      // dummy completion to supercharge
      var equals = true
      for (s <- 0 to 9) {
        for (r <- 0 to 9) {
          for (e <- 0 to 9) {
            for (m <- 0 to 9) {
              val c = ComplianceLevel(success = (s+2), repaired = r, error = e, missing = m)
              if ((c.computePercent().compliance) != CompliancePercent.correctFromLevels(c, 2).compliance) {
                equals = false
              //  println(s"${(c.computePercent().compliance)} is not ${CompliancePercent.correctFromLevels(c, 2).compliance} ")
              }
            }
          }
        }
      }


      // ease on the GC
      System.gc()

      val t0 = System.nanoTime
      val source = complianceLevel.map(_.computePercent().compliance)
      val t1 = System.nanoTime
      // ease on the GC
      System.gc()
      val t4 = System.nanoTime
      val destination2 = complianceLevel.map(_.computeCorrectPercent().compliance)
      val t5 = System.nanoTime

      /*
      // ease on the GC
      System.gc()
      val t2 = System.nanoTime
      val destination = complianceLevel.map(_.complianceWithoutPending())
      val t3 = System.nanoTime
*/



      println(s"Historic compliance computation for ${source.size} completed in ${(t1-t0)/1000} µs")



//      println(s"Direct compliance computation for ${destination.size} completed in ${(t3-t2)/1000} µs")

      println(s"Correct compliance computation for ${destination2.size} completed in ${(t5-t4)/1000} µs")

      (equals === true)
      //and (source === destination)

    }
  }
}
