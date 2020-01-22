/*
 * Copyright 2019-2020 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fortysevendeg.hood.github

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.cats.effect.jvm.Implicits._
import github4s.GithubResponses.{GHResponse, UnexpectedException}
import github4s.free.domain.{Comment, Status}
import io.chrisdavenport.log4cats.Logger

trait GithubService[F[_]] {

  def publishComment(
      accessToken: String,
      owner: String,
      repository: String,
      pullRequestNumber: Int,
      comment: String
  ): F[GHResponse[Comment]]

  def editComment(
      accessToken: String,
      owner: String,
      repository: String,
      commentId: Int,
      comment: String
  ): F[GHResponse[Comment]]

  def listComments(
      accessToken: String,
      owner: String,
      repository: String,
      pullRequestNumber: Int
  ): F[GHResponse[List[Comment]]]

  def createStatus(
      accessToken: String,
      owner: String,
      repository: String,
      pullRequestNumber: Int,
      state: GithubState,
      targetUrl: Option[String],
      description: String,
      context: String
  ): F[GHResponse[Status]]

}

object GithubService {

  def build[F[_]: Sync: Logger]: GithubService[F] = new GithubServiceImpl[F]

  class GithubServiceImpl[F[_]: Sync](implicit L: Logger[F]) extends GithubService[F] {

    def publishComment(
        accessToken: String,
        owner: String,
        repository: String,
        pullRequestNumber: Int,
        comment: String
    ): F[GHResponse[Comment]] =
      for {
        result <- Github(Some(accessToken)).issues
          .createComment(owner, repository, pullRequestNumber, comment)
          .exec()
          .onError { case e => L.error(e)("Found error while accessing GitHub API.") }
        _ <- L.info("Comment sent to GitHub successfully.")
      } yield result

    def editComment(
        accessToken: String,
        owner: String,
        repository: String,
        commentId: Int,
        comment: String
    ): F[GHResponse[Comment]] =
      for {
        result <- Github(Some(accessToken)).issues
          .editComment(owner, repository, commentId, comment)
          .exec()
          .onError { case e => L.error(e)("Found error while accessing GitHub API.") }
        _ <- L.info("Comment edited successfully.")
      } yield result

    def listComments(
        accessToken: String,
        owner: String,
        repository: String,
        pullRequestNumber: Int
    ): F[GHResponse[List[Comment]]] =
      for {
        result <- Github(Some(accessToken)).issues
          .listComments(owner, repository, pullRequestNumber)
          .exec()
          .onError { case e => L.error(e)("Found error while accessing GitHub API.") }
      } yield result

    def createStatus(
        accessToken: String,
        owner: String,
        repository: String,
        pullRequestNumber: Int,
        state: GithubState,
        targetUrl: Option[String],
        description: String,
        context: String
    ): F[GHResponse[Status]] = {
      val gh = Github(Some(accessToken))

      (for {
        pr <- EitherT(gh.pullRequests.get(owner, repository, pullRequestNumber).exec())
        head <- EitherT.fromOption[F](
          pr.result.head,
          UnexpectedException("Couldn't find a head SHA for the specified pull request.")
        )
        sha = head.sha
        result <- EitherT(
          gh.repos
            .createStatus(
              owner,
              repository,
              sha,
              state.value,
              targetUrl,
              description.some,
              context.some
            )
            .exec()
        )
      } yield result).value.onError {
        case e => L.error(e)("Found error while accessing GitHub API.")
      }
    }

  }

}