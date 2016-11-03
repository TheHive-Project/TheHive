(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminObservablesCtrl',
        function($scope, ListSrv, AlertSrv) {
            $scope.dataTypeList = [];
            $scope.params = {
                'newDataTypes': []
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
                    AlertSrv.error('AdminObservablesCtrl', response.data, response.status);
                });
            };
            $scope.load();

            $scope.addArtifactDataTypeList = function() {
                var datatypes = _.without(_.map($scope.params.newDataTypes, function(dt) {
                    return dt.trim();
                }), '', null, undefined);

                angular.forEach(datatypes, function(dt) {
                    ListSrv.save({
                            'listId': 'list_artifactDataType'
                        }, {
                            'value': dt
                        }, function() {
                            $scope.load();
                        },
                        function(response) {
                            AlertSrv.error('ListSrv', response.data, response.status);
                        });
                });
                $scope.params.newDataTypes = '';
            };

            $scope.deleteArtifactDataType = function(datatype) {
                ListSrv['delete']({
                    'listId': datatype.id
                }, function(data) {
                    console.log(data);
                    AlertSrv.log('The datatype ' + datatype.value + ' has been removed', 'success');
                    $scope.load();
                }, function(response) {
                    AlertSrv.error('ListSrv', response.data, response.status);
                });
            };
        });

})();
