/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import javax.inject.Inject

import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import connectors.{DataCacheConnector, LoginConnector}
import controllers.actions._
import config.FrontendAppConfig
import forms.LoginFormProvider
import identifiers.LoginId
import models.{Login, Mode}
import play.api.mvc.Result
import utils.{Navigator, UserAnswers}
import views.html.login

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

class LoginController @Inject()(appConfig: FrontendAppConfig,
                                override val messagesApi: MessagesApi,
                                dataCacheConnector: DataCacheConnector,
                                navigator: Navigator,
                                getData: DataRetrievalAction,
                                requireData: DataRequiredAction,
                                formProvider: LoginFormProvider,
                                loginConnector: LoginConnector) extends FrontendController with I18nSupport {

  val form = formProvider()

  def onPageLoad(mode: Mode) = getData {
    implicit request =>
      val preparedForm = request.userAnswers.flatMap(_.login) match {
        case None => form
        case Some(value) => {
          val loginWithBlankedPassword = Login(value.username, "")
          form.fill(loginWithBlankedPassword)
        }
      }
      Ok(login(appConfig, preparedForm, mode))
  }

  def onSubmit(mode: Mode) = getData.async {
    implicit request =>
      form.bindFromRequest().fold(
        (formWithErrors: Form[_]) =>
          Future.successful(BadRequest(login(appConfig, formWithErrors, mode))),
        value => {
          val encryptedLogin = Login(value.username, value.password, true)

          dataCacheConnector.save[Login](request.externalId, LoginId.toString, encryptedLogin) flatMap { cacheMap =>
            loginConnector.send(encryptedLogin) map {
              case Success(status) => Redirect(navigator.nextPage(LoginId, mode)(new UserAnswers(cacheMap)))
              case Failure(e) => {
                val formWithLoginErrors = form.withGlobalError("Invalid Login Details")
                BadRequest(login(appConfig, formWithLoginErrors, mode))
              }
            }
          }
        }
      )
  }
}
