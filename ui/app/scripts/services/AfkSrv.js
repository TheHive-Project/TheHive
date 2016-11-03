(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AfkSrv', function($rootScope, $q, $modal, $http) {
            var current = null;

            return {
                /**
                 * Ask the user if he's away from keybord
                 *
                 * @return {Promise}
                 */
                prompt: function() {
                    var defer = $q.defer();

                    $http.get('/api/stream/status').then(function(response) {

                        if(response.data.warning === true) {
                            if(current !== null) {
                                defer.reject();
                            } else {
                                var scope = $rootScope.$new(true);

                                scope.ok = function() {
                                    defer.resolve();
                                    current.close();
                                    current = null;
                                };

                                current = $modal.open({
                                    scope: scope,
                                    templateUrl: 'views/partials/afk-modal.html',
                                    size: ''
                                });
                            }
                        } else if(current !== null) {
                            defer.reject();
                            current.close();
                            current = null;
                        }

                    });

                    return defer.promise;
                }
            };
        });
})();
