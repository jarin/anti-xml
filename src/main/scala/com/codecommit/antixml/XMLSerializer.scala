/*
 * Copyright (c) 2011, Daniel Spiewak
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer. 
 * - Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 * - Neither the name of "Anti-XML" nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.codecommit
package antixml

import java.io._

class XMLSerializer(encoding: String, outputDeclaration: Boolean) {

  /** Serializes an XML document, whose root is given.
   *
   * Outputs the XML declaration if this XMLSerializer was created with
   * outputDeclaration = true.
   *
   * Note: it is up to the caller of this function to ensure that the
   * character encoding used by the Writer (if any) matches the encoding of this
   * XMLSerializer
   */
  def serializeDocument(elem: Elem, w: Writer) {
    if (outputDeclaration) {
      w.append("<?xml version=\"1.0\" encoding=\"")
      w.append(encoding)
      w.append("\" standalone=\"yes\"?>")
    }
    serialize(elem, w)
  }

  /** Serializes an XML document, whose root is given.
   *
   * Outputs the XML declaration if this XMLSerializer was created with
   * outputDeclaration = true. 
   *
   * Uses the character encoding of this XMLSerializer (default is UTF-8).
   */
  def serializeDocument(elem: Elem, o: OutputStream) {
    val writer = new OutputStreamWriter(o, encoding)
    serializeDocument(elem, writer)
    writer.flush()
  }

  def serializeDocument(elem: Elem, outputFile: File) {
    val fos = new FileOutputStream(outputFile)
    try {
      serializeDocument(elem, fos)
    } finally {
      fos.close()
    }
  }
  
  def serialize(elem: Elem, w: Writer) {
    /**
     * A stack of prefix/uri declared on each element.
     */
    var namespaces: List[List[(String, String)]] = Nil

    def namespaceDeclared(t: (String, String)) = {
      namespaces.find(_.contains(t)).isDefined
    }

    /**
     * A list of options. Each Some(uri) represents a change in the empty namespace.
     */
    var unprefixedUri: List[Option[String]] = Nil

    def doSerialize(node: Node, w: Writer) {
      node match {
        case Elem(prefix, name, attrs, scope, children) => {
          val ournamespace = scope.findByPrefix(prefix.getOrElse("")).getOrElse(EmptyNamespaceBinding)
          val currentUnprefixedUri = unprefixedUri.find(_.isDefined).map(_.get).getOrElse("")

          val (qname, xmlns): (String, String) = ournamespace match {
            case EmptyNamespaceBinding =>
              if (currentUnprefixedUri == "") {
                unprefixedUri = None :: unprefixedUri
                (name, "")
              }
              else {
                unprefixedUri = Some("") :: unprefixedUri
                (name, " xmlns=\"\"")
              }
            case UnprefixedNamespaceBinding(uri, _) =>
              if (currentUnprefixedUri == uri) {
                unprefixedUri = None :: unprefixedUri
                (name, "")
              }
              else {
                unprefixedUri = Some(uri) :: unprefixedUri
                (name, " xmlns=\"" + uri + "\"")
              }
            case PrefixedNamespaceBinding(pfx, uri, _) =>
              unprefixedUri = None :: unprefixedUri
              val x: String = pfx + ":" + name
              (x, "")
          }

          val newNamespaces = scope.toList.collect({
            case p@PrefixedNamespaceBinding(pfx, uri, _) if !namespaceDeclared((pfx, uri)) => (pfx, uri)
          })
          
          val prefixesStr = newNamespaces.foldLeft("")({(s, t) =>
            s + " xmlns:" + t._1 + "=\"" + t._2 + "\""
          })

          namespaces = newNamespaces :: namespaces

          val attrStr = if (attrs.isEmpty) {
            ""
          } else {
            val delta = attrs map {
              case (key, value) => key.nameForAttribute + "=" + Node.quoteAttribute(value)
            } mkString " "

            " " + delta
          }

          val partial = "<" + qname + xmlns + prefixesStr + attrStr

          if (children.isEmpty) {
            w.append(partial)
            w.append("/>")
          } else {
            w.append(partial)
            w.append('>')
            children foreach { doSerialize(_, w) }
            w append("</")
            w append(qname)
            w append('>')
          }

          namespaces = namespaces.tail
          unprefixedUri = unprefixedUri.tail
        }

        case node => w.append(node.toString)
      }
    }
    doSerialize(elem, w)
  }
}

object XMLSerializer {
  def apply(encoding: String = "UTF-8", outputDeclaration: Boolean = false): XMLSerializer = {
    new XMLSerializer(encoding, outputDeclaration);
  }
}
