/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('AuthenticationCtrl', function($rootScope, $scope, $state, $location, $uibModalStack, $stateParams, AuthenticationSrv, NotificationSrv, UtilsSrv, UrlParser, appConfig) {
            $scope.params = {
                requireMfa: false
            };

            $uibModalStack.dismissAll();

            $scope.ssoEnabled = function() {
                return appConfig.config.authType.indexOf("oauth2") !== -1;
            };


            $scope.login = function() {
                $scope.params.username = $scope.params.username.toLowerCase();
                AuthenticationSrv.login($scope.params.username, $scope.params.password, $scope.params.mfaCode)
                    .then(function() {
                        $location.search('error', null);
                        $state.go('app.index');
                    })
                    .catch(function(err) {
                        if (err.status === 520) {
                            NotificationSrv.error('AuthenticationCtrl', err.data.message, err.status);
                        } else if(err.status === 402){
                            $scope.params.requireMfa = true;
                        } else {
                            NotificationSrv.log(err.data.message, 'error');
                        }
                    });
            };

            var error = UtilsSrv.extractQueryParam('error', UrlParser('query', $location.absUrl()));
            if(!_.isEmpty(error)) {
                $scope.ssoError = window.decodeURIComponent(error).replace(/\+/gi, ' ', '');
            }
        });
})();
