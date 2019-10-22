/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AuthenticationCtrl', function($scope, $state, $location, $uibModalStack, $stateParams, AuthenticationSrv, NotificationSrv, UtilsSrv, UrlParser, appConfig) {
            $scope.params = {};

            $uibModalStack.dismissAll();

            $scope.ssoLogin = function (code, state) {
                AuthenticationSrv.ssoLogin(code, state)
                    .then(function(response) {
                        var redirectLocation = response.headers().location;
                        if(angular.isDefined(redirectLocation)) {
                            window.location = redirectLocation;
                        } else {
                            $state.go('app.index');
                        }
                    })
                    .catch(function(err) {
                        if (err.status === 520) {
                            NotificationSrv.error('AuthenticationCtrl', err.data, err.status);
                        } else {
                            NotificationSrv.log(err.data.message, 'error');
                        }
                        $location.url($location.path());
                    });
            };

            $scope.ssoEnabled = function() {
                return appConfig.config.authType.indexOf("oauth2") !== -1;
            };


            $scope.login = function() {
                $scope.params.username = $scope.params.username.toLowerCase();
                AuthenticationSrv.login($scope.params.username, $scope.params.password)
                  .then(function() {
                      $state.go('app.index');
                  })
                  .catch(function(err) {
                    if (err.status === 520) {
                        NotificationSrv.error('AuthenticationCtrl', err.data.message, err.status);
                    } else {
                        NotificationSrv.log(err.data.message, 'error');
                    }
                });
            };

            var code = UtilsSrv.extractQueryParam('code', UrlParser('query', $location.absUrl()));
            var state = UtilsSrv.extractQueryParam('state', UrlParser('query', $location.absUrl()));
            if((angular.isDefined(code) && angular.isDefined(state)) || $stateParams.autoLogin) {
                $scope.ssoLogin(code, state);
            }
        });
})();
