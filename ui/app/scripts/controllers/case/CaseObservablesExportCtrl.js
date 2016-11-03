(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseObservablesExportCtrl', function($scope, clipboard, UtilsSrv) {

            $scope.exportParams = {
                protect: 'fang',
                format: 'csv'
            };

            $scope.getObservableValue = function(observable) {
                if (observable.attachment) {
                    return observable.attachment.name + '|' + observable.attachment.hashes[0];
                } else {
                    return $scope.protect(observable);
                }
            };

            $scope.protect = function(observable) {
                if ($scope.exportParams.protect === 'fang') {
                    return UtilsSrv.fang(observable);
                }

                return observable.data;
            };

            $scope.copyToClipboard = function() {
                var content = $scope.getCSV();
                var copied = content.map(function(item) {
                    return item.data;
                });

                clipboard.copyText(copied.join('\n'));
            };

            $scope.getDataType = function(observable) {
                return observable.attachment ? 'filename|sha256' : observable.dataType;
            };

            $scope.getCSV = function() {
                var format = $scope.exportParams.format,
                    csv = [];

                if(format === 'txt') {
                    angular.forEach($scope.selection.artifacts, function(observable) {
                        csv.push({data: $scope.getObservableValue(observable)});
                    });
                } else if(format === 'csv') {
                    angular.forEach($scope.selection.artifacts, function(observable) {
                        csv.push({data: observable.dataType + ';' + $scope.getObservableValue(observable)});
                    });
                } else if(format === 'misp') {
                    angular.forEach($scope.selection.artifacts, function(observable) {
                        csv.push({data: '*' + $scope.getDataType(observable) + '\t:' + $scope.getObservableValue(observable)});
                    });
                }

                return csv;
            };
        });

})();
