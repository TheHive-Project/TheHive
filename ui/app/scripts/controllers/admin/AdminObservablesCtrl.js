(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminObservablesCtrl',
        function($scope, ListSrv, NotificationSrv) {
            $scope.dataTypeList = [];
            $scope.params = {
                newDataType: null
            };

            $scope.load = function() {
                ListSrv.query({
                    'listId': 'list_artifactDataType'
                }, {}, function(response) {

                    $scope.dataTypeList = _.keys(response).filter(function(key) {
                        return _.isString(response[key]);
                    }).map(function(key) {
                        return {
                            id: key,
                            value: response[key]
                        };
                    });
                }, function(response) {
                    NotificationSrv.error('AdminObservablesCtrl', response.data, response.status);
                });
            };
            $scope.load();

            $scope.addArtifactDataTypeList = function() {
                ListSrv.save({
                        'listId': 'list_artifactDataType'
                    }, {
                        'value': $scope.params.newDataType
                    }, function() {
                        $scope.load();
                    },
                    function(response) {
                        NotificationSrv.error('ListSrv', response.data, response.status);
                    });

                $scope.params.newDataType = '';
            };

            $scope.deleteArtifactDataType = function(datatype) {
                ListSrv['delete']({
                    'listId': datatype.id
                }, function(data) {
                    console.log(data);
                    NotificationSrv.log('The datatype ' + datatype.value + ' has been removed', 'success');
                    $scope.load();
                }, function(response) {
                    NotificationSrv.error('ListSrv', response.data, response.status);
                });
            };
        });

})();
