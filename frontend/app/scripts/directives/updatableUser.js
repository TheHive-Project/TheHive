(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableUser', function(UserSrv, QuerySrv, UtilsSrv, AuthenticationSrv, NotificationSrv) {
            return {
                restrict: 'E',
                link: function(scope, element, attrs, ctrl, transclude) {
                    var cached = false;

                    UtilsSrv.updatableLink(scope, element, attrs, ctrl, transclude);

                    scope.setValue = function(value) {
                        scope.value = value;
                    };
                    scope.getUserInfo = UserSrv.getCache;

                    scope.$watch('updatable.updating', function(value) {

                        if(value === true && !cached) {
                            var assignableUsers = [];

                            if(_.isFunction(scope.query)) {
                                assignableUsers = scope.query.apply(this, scope.queryParams);
                            } else {
                                assignableUsers = scope.query;
                            }

                            QuerySrv.call('v1', assignableUsers, {
                                filter: {
                                    _field: 'locked',
                                    _value: false
                                },
                                sort: ['+name']
                            })
                            .then(function(users) {
                                scope.userList = users;
                            })
                            .catch(function(err) {
                                NotificationSrv.error('Fetching users', err.data, err.status);
                            });

                            cached = true;
                        }
                    });
                },
                templateUrl: 'views/directives/updatable-user.html',
                scope: {
                    value: '=?',
                    query: '=',
                    queryParams: '=',
                    onUpdate: '&',
                    active: '=?',
                    clearable: '<?'
                }
            };
        });
})();
