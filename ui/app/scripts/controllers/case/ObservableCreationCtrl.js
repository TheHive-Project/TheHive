/**
 * Controller in add new artifact modal page
 */
(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ObservableCreationCtrl',
        function($scope, $stateParams, $modalInstance, clipboard, CaseArtifactSrv, ListSrv, AlertSrv) {

            $scope.activeTlp = 'active';
            $scope.pendingAsync = false;
            $scope.step = 'form';
            $scope.params = {
                bulk: false,
                ioc: false,
                data: '',
                tlp: 2,
                message: '',
                tags: [],
                tagNames: ''
            };

            $scope.$watchCollection('tags', function(value) {
                $scope.params.tagNames = _.pluck(value, 'text').join(',');
            });

            $scope.getDataTypeList = function() {
                ListSrv.query({
                    listId: 'list_artifactDataType'
                }, function(data) {
                    $scope.types = _.filter(_.values(data), _.isString).sort();
                }, function(response) {
                    AlertSrv.error('ObservableCreationCtrl', response.data, response.status);
                });
            };
            $scope.getDataTypeList();
            $scope.updateTlp = function(tlp) {
                $scope.params.tlp = tlp;
            };

            $scope.selectDataType = function(type) {
                $scope.params.dataType = type;
                delete $scope.params.data;
                delete $scope.params.attachment;
            };

            $scope.countObservables = function() {
                if (!$scope.params.data) {
                    return 0;
                }

                var arr = $scope.params.data.split('\n');

                if (arr.length === 0) {
                    return 0;
                }

                return _.without(_.uniq(_.map(arr, function(data) {
                    return data.trim();
                })), '', null, undefined).length;
            };

            $scope.add = function(form) {
                if (!form.$valid) {
                    return;
                }

                var params = $scope.params,
                    count = 1,
                    postData = {
                        dataType: params.dataType,
                        ioc: params.ioc,
                        tlp: params.tlp,
                        message: params.message,
                        tags: _.unique(_.pluck($scope.tags, 'text'))
                    };

                if (params.data) {

                    if ($scope.params.bulk) {
                        postData.data = params.data.split('\n');
                        count = postData.length;
                    } else {
                        postData.data = params.data;
                    }

                    //postData.data = $scope.params.bulk ? params.data.split('\n') : params.data;
                } else if (params.attachment) {
                    postData.attachment = params.attachment;
                }

                $scope.pendingAsync = true;
                CaseArtifactSrv.api().save({
                    caseId: $stateParams.caseId
                }, postData, $scope.handleSaveSuccess, $scope.handleSaveFailure);
            };

            $scope.getFailedObservables = function(failures) {
                return _.map(failures, function(observable) {
                    return {
                        data: observable.object.data,
                        type: observable.type
                    };
                });
            };

            $scope.handleSaveSuccess = function(response) {
                console.log('Observable create modal closed');

                var success = 0,
                    failure = 0;

                if (response.status === 207) {
                    success = response.data.success.length;
                    failure = response.data.failure.length;

                    $scope.failedObservables = $scope.getFailedObservables(response.data.failure);

                    $scope.step = 'error';
                    $scope.pendingAsync = false;

                    AlertSrv.log('Observables have been successfully created', 'success');

                } else {
                    success = angular.isObject(response.data) ? 1 : response.data.length;

                    AlertSrv.log('Observables have been successfully created', 'success');

                    $modalInstance.close(response);
                }
            };

            $scope.handleSaveFailure = function(response) {
                $scope.pendingAsync = false;

                if (response.status === 400) {
                    $scope.failedObservables = $scope.getFailedObservables(response.data);

                    $scope.step = 'error';

                } else {
                    AlertSrv.error('ObservableCreationCtrl', 'An unexpected error occurred while creating the observables', response.status);

                    $modalInstance.close(response);
                }

            };

            $scope.copyToClipboard = function() {
                var copied = _.pluck($scope.failedObservables, 'data');

                clipboard.copyText(copied.join('\n'));
            };

            $scope.cancel = function() {
                $modalInstance.dismiss();
            };

            $scope.isFile = function() {
                if ($scope.params.dataType) {
                    return $scope.params.dataType.endsWith('file');
                } else {
                    return false;
                }
            };
        }
    );

})();
