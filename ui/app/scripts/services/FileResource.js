(function() {
    'use strict';

    angular.module('theHiveServices').factory('FileResource',
        function($resource, $http) {
            function setUrlParams(url, params) {
                angular.forEach(params, function(_, urlParam) {
                    var val = params[urlParam];
                    if (angular.isString(val)) {
                        var encodedVal = encodeURIComponent(val);
                        url = url.replace(new RegExp(':' + urlParam + '(\\W|$)', 'g'), function(match, p1) {
                            return encodedVal + p1;
                        });
                    } else {
                        url = url.replace(new RegExp('(\/?):' + urlParam + '(\\W|$)', 'g'), function(match,
                            leadingSlashes, tail) {
                            if (tail.charAt(0) === '/') {
                                return tail;
                            } else {
                                return leadingSlashes + tail;
                            }
                        });
                    }
                });
                // remove all undefined parameters
                url = url.replace(/:\w+/, '');
                // then replace collapse `/.` if found in the last URL path
                // segment before
                // the query
                // E.g. `http://url.com/id./format?q=x` becomes
                // `http://url.com/id.format?q=x`
                url = url.replace(/\/\.(?=\w+($|\?))/, '.');
                // replace escaped `/\.` with `/.`
                url = url.replace(/\/\\\./, '/.');

                // strip trailing slash
                if (url.substr(-1) === '/') {
                    return url.substr(0, url.length - 1);
                } else {
                    return url;
                }
            }

            return function(url, paramDefaults, actions, options) {
                var res = $resource(url, paramDefaults, actions, options);

                res.save = function(parameters, postData, success, error) {
                    return $http({
                        method: 'POST',
                        url: setUrlParams(url, parameters),
                        headers: {
                            'Content-Type': undefined
                        },
                        transformRequest: function(data) {
                            var formData = new FormData(),
                                copy = angular.copy(data, {}),
                                _json = {};

                            angular.forEach(data, function(value, key) {
                                if(Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
                                    formData.append(key, value);
                                    delete copy[key];
                                } else {
                                    _json[key] = value;
                                }
                            });

                            formData.append("_json", angular.toJson(_json));

                            return formData;
                        },
                        data: postData

                    }).then(success, error);

                };
                return res;
            };
        }
    );

})();
